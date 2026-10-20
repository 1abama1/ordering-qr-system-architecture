package com.example.demo.dto;

import com.example.demo.entity.MenuItem;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** То, что уходит фронту. Сущность наружу не отдаём. */
public record MenuItemDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        Long categoryId,
        String category,
        // Явно задаём имя, иначе Jackson может отдать "available" вместо "isAvailable"
        @JsonProperty("isAvailable") boolean isAvailable
) {
    public static MenuItemDto from(MenuItem item) {
        return new MenuItemDto(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getImageUrl(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.isAvailable()
        );
    }
}
