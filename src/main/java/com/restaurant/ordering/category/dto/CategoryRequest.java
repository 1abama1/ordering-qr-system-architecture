package com.restaurant.ordering.category.dto;
import jakarta.validation.constraints.*;
public record CategoryRequest(@NotBlank @Size(max=100) String name, @NotNull @Min(0) Integer sortOrder) {}
