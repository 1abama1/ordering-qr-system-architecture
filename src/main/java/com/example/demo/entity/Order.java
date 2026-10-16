package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer tableNumber; // Номер столика из QR-кода

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING; // Статус по умолчанию

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(); // Время создания заказа

    @Column(nullable = false)
    private Double totalAmount = 0.0; // Итоговая сумма заказа

    // Связь с позициями внутри заказа (один заказ -> много позиций)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items;
}