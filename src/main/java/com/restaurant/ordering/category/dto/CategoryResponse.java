package com.restaurant.ordering.category.dto;
import com.restaurant.ordering.category.entity.Category;
public record CategoryResponse(Long id, String name, int sortOrder) {
    public static CategoryResponse from(Category c) { return new CategoryResponse(c.getId(), c.getName(), c.getSortOrder()); }
}
