package com.restaurant.ordering.realtime.event;
import com.restaurant.ordering.order.dto.OrderResponse;
import com.restaurant.ordering.order.entity.OrderStatus;
import com.restaurant.ordering.menu.dto.MenuItemResponse;
import java.time.Instant;
import java.util.UUID;
public final class Events {
    public static final String KITCHEN = "/topic/kitchen/orders";
    public static final String MENU = "/topic/menu/updates";
    private Events() {}
    public record NewOrder(UUID eventId, String type, Instant occurredAt, Long orderId, OrderResponse order) {}
    public record StatusChanged(UUID eventId, String type, Instant occurredAt, Long orderId,
        OrderStatus previousStatus, OrderStatus newStatus, Instant completedAt, long version, OrderResponse order) {}
    public record MenuChanged(UUID eventId, String type, Instant occurredAt, Long menuItemId,
        Boolean isAvailable, Boolean isActive, Instant updatedAt, Long version) {}
    public static OutboundEvent newOrder(OrderResponse o) {
        return new OutboundEvent(KITCHEN, new NewOrder(UUID.randomUUID(), "NEW_ORDER", Instant.now(), o.id(), o));
    }
    public static OutboundEvent statusChanged(OrderResponse o, OrderStatus previous) {
        return new OutboundEvent(KITCHEN, new StatusChanged(UUID.randomUUID(), "ORDER_STATUS_CHANGED", Instant.now(),
            o.id(), previous, o.status(), o.completedAt(), o.version(), o));
    }
    public static OutboundEvent menuChanged(MenuItemResponse m, boolean availabilityOnly) {
        return new OutboundEvent(MENU, new MenuChanged(UUID.randomUUID(), availabilityOnly ? "MENU_ITEM_AVAILABILITY_CHANGED" : "MENU_CHANGED",
            Instant.now(), m.id(), m.isAvailable(), m.isActive(), m.updatedAt(), m.version()));
    }
    public static OutboundEvent categoriesChanged() {
        return new OutboundEvent(MENU, new MenuChanged(UUID.randomUUID(), "CATEGORIES_CHANGED", Instant.now(), null, null, null, null, null));
    }
}
