package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long menuItemId; // ID блюда из меню

    @Column(nullable = false)
    private Integer quantity; // Количество порций

    @Column(nullable = false)
    private Double priceAtOrder; // Цена блюда на момент заказа
}