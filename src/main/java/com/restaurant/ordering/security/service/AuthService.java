package com.restaurant.ordering.security.service;
import com.restaurant.ordering.common.config.AppProperties;
import com.restaurant.ordering.security.dto.AuthDtos;
import com.restaurant.ordering.security.entity.StaffUser;
import com.restaurant.ordering.security.repository.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class AuthService {
    private final StaffUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final AppProperties properties;
    // An unknown username still incurs BCrypt work, reducing timing differences.
    private final String dummyHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Transactional(readOnly=true)
    public AuthDtos.Token login(AuthDtos.Login r) {
        var user = users.findByUsername(r.username());
        boolean matches = r.password().getBytes(StandardCharsets.UTF_8).length <= 72 &&
            passwords.matches(r.password(), user.map(StaffUser::getPasswordHash).orElse(dummyHash));
        if (!matches || user.isEmpty() || !user.get().isEnabled()) throw new BadCredentialsException("Invalid credentials");
        Instant now = Instant.now(), expires = now.plusSeconds(properties.jwtTtlMinutes() * 60L);
        var claims = JwtClaimsSet.builder().issuer("restaurant-ordering").audience(List.of("ordering-staff"))
            .subject(user.get().getUsername()).issuedAt(now).expiresAt(expires).id(UUID.randomUUID().toString()).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthDtos.Token(token, "Bearer", expires, AuthDtos.Staff.from(user.get()));
    }
    @Transactional(readOnly=true)
    public AuthDtos.Staff me(String username) { return AuthDtos.Staff.from(users.findByUsername(username).orElseThrow(() -> new BadCredentialsException("Account unavailable"))); }
    @Transactional
    public AuthDtos.Staff create(AuthDtos.CreateStaff r) {
        PasswordPolicy.validate(r.password());
        return AuthDtos.Staff.from(users.saveAndFlush(new StaffUser(r.username(), passwords.encode(r.password()), r.role())));
    }
}
