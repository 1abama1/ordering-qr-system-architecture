package com.restaurant.ordering.menu.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record MenuItemRequest(
    @NotBlank @Size(max=200) String name,
    @Size(max=4000) String description,
    @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer=7, fraction=2) BigDecimal price,
    @Size(max=2000) @Pattern(regexp="^https?://[^\\s]+$", message="must be an HTTP(S) URL") String imageUrl,
    @NotNull @Positive Long categoryId,
    @NotNull Boolean isAvailable, @NotNull Boolean isActive) {}
