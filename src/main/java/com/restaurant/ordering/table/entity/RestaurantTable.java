package com.restaurant.ordering.table.entity;
import com.restaurant.ordering.common.entity.TimestampedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "restaurant_tables") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantTable extends TimestampedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private int number;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(nullable = false) private int qrTokenVersion = 1;
    public RestaurantTable(int number) { this.number = number; }
    public void setActive(boolean active) { this.active = active; }
    public void regenerateToken() { qrTokenVersion = Math.incrementExact(qrTokenVersion); }
}
