package com.restaurant.ordering.common.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
@Component @RequiredArgsConstructor
public class Problems {
    private final ObjectMapper mapper;
    private final com.restaurant.ordering.common.config.AppProperties properties;
    public static ProblemDetail create(HttpStatusCode status, String code, String detail, String path) {
        var p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setInstance(URI.create(path)); p.setProperty("code", code); p.setProperty("timestamp", Instant.now());
        p.setProperty("path", path); p.setProperty("correlationId", MDC.get("correlationId")); return p;
    }
    public void write(HttpServletResponse response, HttpStatus status, String code, String detail, HttpServletRequest request) throws IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && properties.allowedOrigins().contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin); response.addHeader("Vary", "Origin");
            response.setHeader("Access-Control-Expose-Headers", "X-Correlation-ID, Retry-After");
        }
        response.setStatus(status.value()); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), create(status, code, detail, request.getRequestURI()));
    }
}
