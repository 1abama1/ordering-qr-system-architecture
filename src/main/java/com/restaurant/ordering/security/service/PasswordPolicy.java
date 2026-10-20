package com.restaurant.ordering.security.service;
import com.restaurant.ordering.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void validate(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.badRequest("VALIDATION_ERROR", "Password must have at least 12 characters and at most 72 UTF-8 bytes");
    }
}
