package com.restaurant.ordering;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.*;

final class TestDatabase {
    private static final Map<String, PostgreSQLContainer<?>> CONTAINERS = new HashMap<>();
    private TestDatabase() {}
    static synchronized void configure(DynamicPropertyRegistry properties, String database) {
        String server = System.getenv("IT_DB_SERVER");
        if (server != null && !server.isBlank()) {
            String username = Objects.requireNonNull(System.getenv("IT_DB_USERNAME"), "IT_DB_USERNAME is required");
            String password = Objects.requireNonNull(System.getenv("IT_DB_PASSWORD"), "IT_DB_PASSWORD is required");
            properties.add("spring.datasource.url", () -> server + "/" + database);
            properties.add("spring.datasource.username", () -> username);
            properties.add("spring.datasource.password", () -> password);
        } else {
            var container = CONTAINERS.computeIfAbsent(database, name -> {
                var pg = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName(name);
                pg.start(); return pg;
            });
            properties.add("spring.datasource.url", container::getJdbcUrl);
            properties.add("spring.datasource.username", container::getUsername);
            properties.add("spring.datasource.password", container::getPassword);
        }
    }
}
