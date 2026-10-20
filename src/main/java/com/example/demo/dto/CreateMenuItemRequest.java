package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Тело запроса POST /api/menu.
 * Категория передаётся по названию: если такой ещё нет, она создастся.
 */
public record CreateMenuItemRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        String imageUrl,
        @NotBlank String category,
        @JsonProperty("isAvailable") Boolean isAvailable // необязательное, по умолчанию true
) {
}
