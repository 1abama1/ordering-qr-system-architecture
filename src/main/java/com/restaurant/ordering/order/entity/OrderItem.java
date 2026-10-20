package com.restaurant.ordering.order.entity;
import com.restaurant.ordering.menu.entity.MenuItem;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "order_items") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") private Order order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "menu_item_id") private MenuItem menuItem;
    @Column(nullable = false, length = 200) private String menuItemNameSnapshot;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal priceAtOrder;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal lineTotal;
    public OrderItem(MenuItem item, int quantity) {
        menuItem = item; menuItemNameSnapshot = item.getName(); this.quantity = quantity;
        priceAtOrder = item.getPrice(); lineTotal = priceAtOrder.multiply(BigDecimal.valueOf(quantity));
    }
    void attachTo(Order order) { this.order = order; }
}
