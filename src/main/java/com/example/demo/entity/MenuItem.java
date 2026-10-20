package com.example.demo.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private String imageUrl;

    // Поле в Java называется "available", колонка в БД — "is_available".
    // Так Spring Data понимает метод findByAvailableTrue(), а в JSON мы отдаём "isAvailable" через DTO.
    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    protected MenuItem() {
        // для JPA
    }

    public MenuItem(String name, String description, BigDecimal price, String imageUrl, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.category = category;
    }

    /** Переключает флаг стоп-листа и возвращает новое значение. */
    public boolean toggleAvailability() {
        this.available = !this.available;
        return this.available;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public boolean isAvailable() { return available; }
    public Category getCategory() { return category; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setCategory(Category category) { this.category = category; }
}
