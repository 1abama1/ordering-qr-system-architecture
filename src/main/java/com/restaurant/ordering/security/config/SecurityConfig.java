package com.restaurant.ordering.security.config;
import com.restaurant.ordering.common.api.Problems;
import com.restaurant.ordering.common.config.AppProperties;
import com.restaurant.ordering.security.service.StaffJwtAuthentication;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean JwtEncoder jwtEncoder(AppProperties p) { return new NimbusJwtEncoder(new ImmutableSecret<>(p.jwtSecret().getBytes(StandardCharsets.UTF_8))); }
    @Bean JwtDecoder jwtDecoder(AppProperties p) {
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(p.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("ordering-staff")
            ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(java.time.Duration.ZERO), new JwtIssuerValidator("restaurant-ordering"), audience));
        return decoder;
    }
    @Bean CorsConfigurationSource corsConfigurationSource(AppProperties p) {
        var config = new CorsConfiguration(); config.setAllowedOrigins(p.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Idempotency-Key",
            "X-Correlation-ID", "ngrok-skip-browser-warning"
        ));
        config.setExposedHeaders(List.of("Location", "Idempotency-Replayed", "X-Correlation-ID", "Retry-After"));
        config.setAllowCredentials(false); config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;
    }
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, Problems problems, StaffJwtAuthentication authentication) throws Exception {
        http.csrf(c -> c.disable()).cors(c -> {}).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/menu", "/api/categories", "/api/tables/resolve", "/ws", "/actuator/health", "/actuator/health/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders", "/api/staff/auth/login").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/menu/*/availability", "/api/menu/*/toggle").hasAnyRole("ADMIN", "KITCHEN")
                .requestMatchers("/api/staff/tables/**", "/api/staff/users", "/api/menu/**", "/api/categories/**").hasRole("ADMIN")
                .requestMatchers("/api/orders/**", "/api/staff/menu", "/api/staff/auth/me").hasAnyRole("ADMIN", "KITCHEN")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(authentication))
                .authenticationEntryPoint((r,s,e) -> problems.write(s, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required or token invalid", r)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((r,s,x) -> problems.write(s, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", r))
                .accessDeniedHandler((r,s,x) -> problems.write(s, HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions", r)))
            .headers(h -> h.referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=()")));
        return http.build();
    }
}
