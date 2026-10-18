package com.restaurant.ordering.category.entity;
import com.restaurant.ordering.common.entity.TimestampedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "categories") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends TimestampedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String name;
    @Column(nullable = false) private int sortOrder;
    public Category(String name, int sortOrder) { update(name, sortOrder); }
    public void update(String name, int sortOrder) { this.name = name.trim(); this.sortOrder = sortOrder; }
}
