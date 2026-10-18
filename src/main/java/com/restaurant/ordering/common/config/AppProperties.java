package com.restaurant.ordering.common.config;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.util.List;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        @NotBlank @Size(min = 32) String qrSecret,
        @NotNull URI guestMenuUrl,
        @NotEmpty List<@NotBlank String> allowedOrigins,
        @Min(1) @Max(60) int jwtTtlMinutes,
        @Min(1) int orderRateLimit,
        @Min(1) int loginRateLimit,
        @Min(1) int qrRateLimit) {
    public AppProperties {
        if (allowedOrigins != null && allowedOrigins.stream().anyMatch(o -> o.contains("*")))
            throw new IllegalArgumentException("Exact allowed origins are required");
        if (guestMenuUrl != null && (!List.of("http", "https").contains(guestMenuUrl.getScheme())
                || guestMenuUrl.getHost() == null || guestMenuUrl.getQuery() != null || guestMenuUrl.getFragment() != null))
            throw new IllegalArgumentException("guest-menu-url must be an absolute HTTP(S) URL without query/fragment");
    }
}
