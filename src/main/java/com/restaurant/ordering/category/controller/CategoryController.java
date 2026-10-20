package com.restaurant.ordering.category.controller;
import com.restaurant.ordering.category.dto.*;
import com.restaurant.ordering.category.service.CategoryService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
@RestController @RequestMapping("/api/categories") @RequiredArgsConstructor @Tag(name="Categories")
public class CategoryController {
    private final CategoryService service;
    @GetMapping @Operation(summary="Public categories ordered by sortOrder and id")
    public List<CategoryResponse> list() { return service.list(); }
    @PostMapping @Operation(summary="Create category (ADMIN)") @SecurityRequirement(name="bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest r) {
        var result = service.create(r); return ResponseEntity.created(URI.create("/api/categories/" + result.id())).body(result);
    }
    @PutMapping("/{id}") @Operation(summary="Update category (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) { return service.update(id,r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Delete unused category (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public void delete(@PathVariable Long id) { service.delete(id); }
}
