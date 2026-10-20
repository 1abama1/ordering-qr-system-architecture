package com.restaurant.ordering.order.dto;
import com.restaurant.ordering.order.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record OrderResponse(Long id, int tableNumber, OrderStatus status, BigDecimal totalAmount, String currency,
    Instant createdAt, Instant updatedAt, Instant completedAt, long version, List<Item> items) {
    public record Item(Long menuItemId, String menuItemName, int quantity, BigDecimal priceAtOrder, BigDecimal lineTotal) {}
    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getTableNumberSnapshot(), o.getStatus(), o.getTotalAmount(), "KGS",
            o.getCreatedAt(), o.getUpdatedAt(), o.getCompletedAt(), o.getVersion(),
            o.getItems().stream().map(i -> new Item(i.getMenuItem().getId(), i.getMenuItemNameSnapshot(), i.getQuantity(), i.getPriceAtOrder(), i.getLineTotal())).toList());
    }
}
