package com.restaurant.ordering.common.config;
import com.restaurant.ordering.common.api.Problems;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.time.Clock;
import java.util.*;

@Component @Order(Ordered.HIGHEST_PRECEDENCE + 10) @RequiredArgsConstructor
public class RequestGuardFilter extends OncePerRequestFilter {
    private static final int MAX_BODY = 32768;
    private final Problems problems;
    private final AppProperties properties;
    private final Map<String, Window> limits = new HashMap<>();
    private record Window(long minute, int count) {}
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        var incoming = request.getHeader("X-Correlation-ID");
        var correlation = incoming != null && incoming.matches("[a-zA-Z0-9-]{1,64}") ? incoming : UUID.randomUUID().toString();
        MDC.put("correlationId", correlation); response.setHeader("X-Correlation-ID", correlation);
        try {
            String path = request.getServletPath();
            boolean post = "POST".equals(request.getMethod());
            int limit = post && path.equals("/api/orders") ? properties.orderRateLimit()
                : post && path.equals("/api/staff/auth/login") ? properties.loginRateLimit()
                : path.equals("/api/tables/resolve") || path.equals("/ws") ? properties.qrRateLimit() : 0;
            if (limit > 0 && !allow(request.getRemoteAddr() + ":" + path, limit)) {
                response.setHeader("Retry-After", "60");
                problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests; retry later", request); return;
            }
            if (Set.of("POST", "PUT", "PATCH").contains(request.getMethod()) && path.startsWith("/api/")) {
                if (request.getContentLengthLong() > MAX_BODY) {
                    problems.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "REQUEST_TOO_LARGE", "Maximum request size is 32 KiB", request); return;
                }
                byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) {
                    problems.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "REQUEST_TOO_LARGE", "Maximum request size is 32 KiB", request); return;
                }
                chain.doFilter(wrap(request, body), response);
            } else chain.doFilter(request, response);
        } finally { MDC.remove("correlationId"); }
    }
    private synchronized boolean allow(String key, int limit) {
        long minute = Clock.systemUTC().millis() / 60000;
        if (limits.size() >= 10000) limits.entrySet().removeIf(e -> e.getValue().minute() != minute);
        Window w = limits.get(key);
        if (w == null && limits.size() >= 10000) return false;
        int count = w == null || w.minute() != minute ? 0 : w.count();
        if (count >= limit) return false;
        limits.put(key, new Window(minute, count + 1)); return true;
    }
    private HttpServletRequest wrap(HttpServletRequest request, byte[] body) {
        return new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous MVC only"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8)); }
        };
    }
}
