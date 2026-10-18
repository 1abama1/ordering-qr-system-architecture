package com.restaurant.ordering.menu.dto;
import com.restaurant.ordering.menu.entity.MenuItem;
import java.math.BigDecimal;
import java.time.Instant;
public record MenuItemResponse(Long id, String name, String description, BigDecimal price, String currency,
    String imageUrl, Long categoryId, boolean isAvailable, boolean isActive, Instant updatedAt, long version) {
    public static MenuItemResponse from(MenuItem m) {
        return new MenuItemResponse(m.getId(), m.getName(), m.getDescription(), m.getPrice(), "KGS", m.getImageUrl(),
            m.getCategory().getId(), m.isAvailable(), m.isActive(), m.getUpdatedAt(), m.getVersion());
    }
}
