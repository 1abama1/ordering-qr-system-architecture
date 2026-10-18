package com.restaurant.ordering.menu.controller;
import com.restaurant.ordering.menu.dto.*;
import com.restaurant.ordering.menu.service.MenuService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
@RestController @RequiredArgsConstructor @Tag(name="Menu")
public class MenuController {
    private final MenuService service;
    @GetMapping("/api/menu") @Operation(summary="Public active menu", description="includeUnavailable=true includes disabled stop-list items. Inactive items are always hidden.")
    public List<MenuItemResponse> menu(@RequestParam(defaultValue="false") boolean includeUnavailable) { return service.publicMenu(includeUnavailable); }
    @GetMapping("/api/staff/menu") @Operation(summary="All menu items (ADMIN, KITCHEN)") @SecurityRequirement(name="bearerAuth")
    public List<MenuItemResponse> staff() { return service.staffMenu(); }
    @PostMapping("/api/menu") @Operation(summary="Create menu item (ADMIN)") @SecurityRequirement(name="bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemRequest r) {
        var result = service.create(r); return ResponseEntity.created(URI.create("/api/menu/" + result.id())).body(result);
    }
    @PutMapping("/api/menu/{id}") @Operation(summary="Replace menu item (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public MenuItemResponse update(@PathVariable Long id, @Valid @RequestBody MenuItemRequest r) { return service.update(id,r); }
    @DeleteMapping("/api/menu/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Deactivate menu item (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public void delete(@PathVariable Long id) { service.deactivate(id); }
    @PatchMapping("/api/menu/{id}/availability") @Operation(summary="Set availability explicitly (ADMIN, KITCHEN)") @SecurityRequirement(name="bearerAuth")
    public MenuItemResponse availability(@PathVariable Long id, @Valid @RequestBody AvailabilityRequest r) { return service.availability(id,r.isAvailable()); }
    @PatchMapping("/api/menu/{id}/toggle") @Operation(summary="Toggle availability (ADMIN, KITCHEN)", description="Compatibility only: retries may reverse the value again. Prefer /availability.") @SecurityRequirement(name="bearerAuth")
    public MenuItemResponse toggle(@PathVariable Long id) { return service.toggle(id); }
}
