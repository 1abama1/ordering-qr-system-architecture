package com.restaurant.ordering.order.dto;
import com.restaurant.ordering.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
public record StatusRequest(@NotNull OrderStatus status) {}
