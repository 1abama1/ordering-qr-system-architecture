package com.restaurant.ordering.common.entity;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
@Getter @MappedSuperclass
public abstract class TimestampedEntity {
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist protected void created() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate protected void updated() { updatedAt = Instant.now(); }
}
