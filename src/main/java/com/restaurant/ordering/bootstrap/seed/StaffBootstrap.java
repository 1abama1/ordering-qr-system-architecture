package com.restaurant.ordering.bootstrap.seed;
import com.restaurant.ordering.security.entity.StaffUser;
import com.restaurant.ordering.security.repository.StaffUserRepository;
import com.restaurant.ordering.security.service.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor @Slf4j
public class StaffBootstrap implements CommandLineRunner {
    private final StaffUserRepository users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    @Value("${BOOTSTRAP_ADMIN_USERNAME:}") private String username;
    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") private String password;
    @Override @Transactional
    public void run(String... args) {
        if (username.isBlank() && password.isBlank()) return;
        if (!username.matches("[a-zA-Z0-9._-]{3,100}") || password.isBlank())
            throw new IllegalStateException("Provide valid BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD together");
        PasswordPolicy.validate(password);
        jdbc.queryForList("select pg_advisory_xact_lock(191021)");
        if (users.findByUsername(username).isEmpty()) {
            users.saveAndFlush(new StaffUser(username, passwords.encode(password), StaffUser.Role.ADMIN));
            log.info("Bootstrap administrator created; remove bootstrap credentials from deployment environment");
        }
    }
}
