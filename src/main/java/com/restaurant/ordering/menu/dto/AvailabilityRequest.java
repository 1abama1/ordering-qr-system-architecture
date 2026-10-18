package com.restaurant.ordering.menu.dto;
import jakarta.validation.constraints.NotNull;
public record AvailabilityRequest(@NotNull Boolean isAvailable) {}
