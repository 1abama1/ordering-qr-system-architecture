package com.restaurant.ordering.menu.entity;
import com.restaurant.ordering.category.entity.Category;
import com.restaurant.ordering.common.entity.TimestampedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "menu_items") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItem extends TimestampedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(columnDefinition = "text") private String imageUrl;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id") private Category category;
    @Column(name = "is_available", nullable = false) private boolean available;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Version private long version;
    public MenuItem(String name, String description, BigDecimal price, String imageUrl, Category category, boolean available, boolean active) {
        update(name, description, price, imageUrl, category, available, active);
    }
    public void update(String name, String description, BigDecimal price, String imageUrl, Category category, boolean available, boolean active) {
        this.name = name.trim(); this.description = description; this.price = price.setScale(2);
        this.imageUrl = imageUrl; this.category = category; this.available = available; this.active = active;
    }
    public void setAvailability(boolean value) { available = value; }
    public void deactivate() { active = false; }
}
