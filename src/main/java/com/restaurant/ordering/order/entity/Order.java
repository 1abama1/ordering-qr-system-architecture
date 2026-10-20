package com.restaurant.ordering.order.entity;
import com.restaurant.ordering.common.entity.TimestampedEntity;
import com.restaurant.ordering.common.exception.ApiException;
import com.restaurant.ordering.table.entity.RestaurantTable;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity @Table(name = "orders") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends TimestampedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "table_id") private RestaurantTable table;
    @Column(nullable = false) private int tableNumberSnapshot;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OrderStatus status = OrderStatus.PENDING;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal totalAmount = new BigDecimal("0.00");
    private Instant completedAt;
    @Column(nullable = false, unique = true, length = 36) private String idempotencyKey;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Version private long version;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC") private List<OrderItem> items = new ArrayList<>();
    public Order(RestaurantTable table, String key, String fingerprint) {
        this.table = table; tableNumberSnapshot = table.getNumber(); idempotencyKey = key; requestFingerprint = fingerprint;
    }
    public void addItem(OrderItem item) {
        item.attachTo(this); items.add(item); totalAmount = totalAmount.add(item.getLineTotal());
    }
    public boolean changeStatus(OrderStatus target) {
        if (target == status) return false;
        if (status == OrderStatus.COMPLETED || target == OrderStatus.PENDING)
            throw ApiException.conflict("INVALID_ORDER_STATUS_TRANSITION", "Cannot change status from " + status + " to " + target);
        status = target;
        if (target == OrderStatus.COMPLETED) completedAt = Instant.now();
        return true;
    }
}
