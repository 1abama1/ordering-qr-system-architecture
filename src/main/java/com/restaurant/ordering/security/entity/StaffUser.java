package com.restaurant.ordering.security.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "staff_users") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffUser {
    public enum Role { ADMIN, KITCHEN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String username;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    public StaffUser(String username, String passwordHash, Role role) {
        this.username = username; this.passwordHash = passwordHash; this.role = role;
    }
}
