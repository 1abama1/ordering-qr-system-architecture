package com.example.demo.controller;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.OrderStatus;
import com.example.demo.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // 1. Создание заказа с телефона гостя (QR-код)
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = new Order();
        order.setTableNumber(request.getTableNumber());
        order.setStatus(OrderStatus.PENDING);

        List<OrderItem> items = new ArrayList<>();
        double totalAmount = 0.0;

        if (request.getItems() != null) {
            for (CreateOrderRequest.OrderItemDto itemDto : request.getItems()) {
                OrderItem item = new OrderItem();
                item.setMenuItemId(itemDto.getMenuItemId());
                item.setQuantity(itemDto.getQuantity());

                double itemPrice = itemDto.getPrice() != null ? itemDto.getPrice() : 0.0;
                item.setPriceAtOrder(itemPrice);

                totalAmount += itemPrice * itemDto.getQuantity();
                items.add(item);
            }
        }

        order.setItems(items);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);
        return ResponseEntity.ok(savedOrder);
    }

    // 2. Получение активных заказов для экрана кухни (KDS)
    @GetMapping("/active")
    public ResponseEntity<List<Order>> getActiveOrders() {
        List<Order> activeOrders = orderRepository.findByStatusNot(OrderStatus.COMPLETED);
        return ResponseEntity.ok(activeOrders);
    }

    // 3. Изменение статуса заказа поваром/официантом (PENDING -> COOKING -> COMPLETED)
    @PatchMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return orderRepository.findById(id)
                .map(order -> {
                    order.setStatus(status);
                    Order updatedOrder = orderRepository.save(order);
                    return ResponseEntity.ok(updatedOrder);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}