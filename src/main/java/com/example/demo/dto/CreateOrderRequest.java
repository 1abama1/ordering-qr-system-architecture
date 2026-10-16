package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class CreateOrderRequest {
    private Integer tableNumber;
    private List<OrderItemDto> items;

    @Getter
    @Setter
    public static class OrderItemDto {
        private Long menuItemId;
        private Integer quantity;
        private Double price; // Цена за единицу блюда
    }
}