package com.restaurant.ordering.order.service;
import com.restaurant.ordering.order.dto.*;
import com.restaurant.ordering.order.entity.*;
import com.restaurant.ordering.order.repository.OrderRepository;
import com.restaurant.ordering.menu.repository.MenuItemRepository;
import com.restaurant.ordering.table.service.TableService;
import com.restaurant.ordering.common.exception.ApiException;
import com.restaurant.ordering.realtime.event.Events;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class OrderService {
    public record Created(OrderResponse order, boolean replayed) {}
    private final OrderRepository orders;
    private final MenuItemRepository menu;
    private final TableService tables;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final MeterRegistry metrics;
    @Transactional
    public Created create(CreateOrderRequest request, String rawKey) {
        var sample = io.micrometer.core.instrument.Timer.start(metrics);
        try {
            var key = normalizeKey(rawKey);
            var quantities = new TreeMap<Long, Integer>();
            for (var item : request.items()) {
                if (quantities.putIfAbsent(item.menuItemId(), item.quantity()) != null)
                    throw ApiException.badRequest("DUPLICATE_MENU_ITEM", "Duplicate menuItemId is not allowed");
            }
            // PostgreSQL transaction advisory lock survives multiple application instances and concurrent retries.
            // A hash collision only serializes unrelated keys; the UNIQUE constraint remains the final invariant.
            jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", key);
            var table = tables.validateForOrder(request.tableNumber(), request.tableToken());
            var fingerprint = fingerprint(table.getId(), quantities);
            var existing = orders.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                if (!existing.get().getRequestFingerprint().equals(fingerprint))
                    throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for different contents");
                return new Created(OrderResponse.from(existing.get()), true);
            }
            var locked = menu.lockAllById(quantities.keySet());
            if (locked.size() != quantities.size()) throw ApiException.notFound("MENU_ITEM_NOT_FOUND", "One or more menu items do not exist");
            var order = new Order(table, key, fingerprint);
            for (var item : locked) {
                if (!item.isActive() || !item.isAvailable())
                    throw ApiException.conflict("MENU_ITEM_UNAVAILABLE", "Menu item " + item.getId() + " is unavailable");
                order.addItem(new OrderItem(item, quantities.get(item.getId())));
            }
            if (order.getTotalAmount().compareTo(new BigDecimal("9999999999.99")) > 0)
                throw ApiException.badRequest("ORDER_TOTAL_TOO_LARGE", "Order total exceeds the supported limit");
            orders.saveAndFlush(order);
            var response = OrderResponse.from(order); events.publishEvent(Events.newOrder(response));
            log.info("Order created orderId={} tableNumber={} totalAmount={}", order.getId(), order.getTableNumberSnapshot(), order.getTotalAmount());
            return new Created(response, false);
        } finally { sample.stop(metrics.timer("ordering.order.create")); }
    }
    @Transactional(readOnly=true)
    public List<OrderResponse> active(int page, int size) {
        var ids = orders.activeIds(List.of(OrderStatus.PENDING, OrderStatus.COOKING), PageRequest.of(page, size));
        return ids.isEmpty() ? List.of() : orders.findDetailedByIds(ids).stream()
            .filter(o -> o.getStatus() != OrderStatus.COMPLETED).map(OrderResponse::from).toList();
    }
    @Transactional(readOnly=true)
    public OrderResponse get(Long id) {
        return orders.findDetailedByIds(List.of(id)).stream().findFirst().map(OrderResponse::from)
            .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order does not exist"));
    }
    @Transactional
    public OrderResponse status(Long id, OrderStatus status) {
        var order = orders.lockById(id).orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order does not exist"));
        var previous = order.getStatus();
        if (!order.changeStatus(status)) return OrderResponse.from(order);
        orders.flush(); var response = OrderResponse.from(order); events.publishEvent(Events.statusChanged(response, previous));
        log.info("Order status changed orderId={} from={} to={} version={}", id, previous, status, order.getVersion());
        return response;
    }
    private String normalizeKey(String rawKey) {
        if (rawKey == null || !rawKey.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be a UUID");
        return UUID.fromString(rawKey).toString();
    }
    private String fingerprint(Long tableId, SortedMap<Long, Integer> items) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((tableId + ":" + items).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
