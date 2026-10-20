package com.restaurant.ordering.security.controller;
import com.restaurant.ordering.security.dto.AuthDtos;
import com.restaurant.ordering.security.service.AuthService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequiredArgsConstructor @Tag(name="Staff")
public class AuthController {
    private final AuthService service;
    @PostMapping("/api/staff/auth/login") @Operation(summary="Staff login; returns 15-minute Bearer JWT")
    public ResponseEntity<AuthDtos.Token> login(@Valid @RequestBody AuthDtos.Login r) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.login(r));
    }
    @GetMapping("/api/staff/auth/me") @Operation(summary="Current staff account") @SecurityRequirement(name="bearerAuth")
    public AuthDtos.Staff me(Principal principal) { return service.me(principal.getName()); }
    @PostMapping("/api/staff/users") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Create staff account (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public AuthDtos.Staff create(@Valid @RequestBody AuthDtos.CreateStaff r) { return service.create(r); }
}
