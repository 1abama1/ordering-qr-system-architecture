package com.restaurant.ordering.security.dto;
import com.restaurant.ordering.security.entity.StaffUser;
import jakarta.validation.constraints.*;
import java.time.Instant;
public final class AuthDtos {
    private AuthDtos() {}
    public record Login(@NotBlank @Size(max=100) String username, @NotBlank @Size(max=72) String password) {
        @Override public String toString() { return "Login[REDACTED]"; }
    }
    public record CreateStaff(@NotBlank @Pattern(regexp="[a-zA-Z0-9._-]{3,100}") String username,
        @NotBlank @Size(min=12, max=72) String password, @NotNull StaffUser.Role role) {
        @Override public String toString() { return "CreateStaff[REDACTED]"; }
    }
    public record Staff(Long id, String username, StaffUser.Role role) {
        public static Staff from(StaffUser u) { return new Staff(u.getId(), u.getUsername(), u.getRole()); }
    }
    public record Token(String accessToken, String tokenType, Instant expiresAt, Staff staff) {}
}
