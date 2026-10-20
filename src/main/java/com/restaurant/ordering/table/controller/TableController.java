package com.restaurant.ordering.table.controller;
import com.restaurant.ordering.table.dto.TableDtos;
import com.restaurant.ordering.table.service.TableService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController @RequiredArgsConstructor @Validated @Tag(name="Tables and QR")
public class TableController {
    private final TableService service;
    @GetMapping("/api/tables/resolve") @Operation(summary="Verify a QR table and token", description="Use query parameters tableNumber and tableToken from the QR guest URL (table and token).")
    public TableDtos.Response resolve(@RequestParam @Positive int tableNumber, @RequestParam @NotBlank @Size(max=256) String tableToken) { return service.resolve(tableNumber,tableToken); }
    @GetMapping("/api/staff/tables") @Operation(summary="List tables (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public List<TableDtos.Response> list() { return service.list(); }
    @PostMapping("/api/staff/tables") @Operation(summary="Create table (ADMIN)") @SecurityRequirement(name="bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TableDtos.Response> create(@Valid @RequestBody TableDtos.Create r) {
        var result = service.create(r.number()); return ResponseEntity.created(URI.create("/api/staff/tables/" + result.id())).body(result);
    }
    @PatchMapping("/api/staff/tables/{id}") @Operation(summary="Activate or deactivate table (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public TableDtos.Response update(@PathVariable Long id, @Valid @RequestBody TableDtos.Update r) { return service.update(id,r.isActive()); }
    @GetMapping("/api/staff/tables/{id}/qr") @Operation(summary="QR metadata by default; format=png returns image (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public ResponseEntity<?> qr(@PathVariable Long id, @RequestParam(defaultValue="json") @Pattern(regexp="json|png") String format) {
        var qr = service.qr(id);
        return format.equals("png") ? ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(service.png(qr))
            : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(qr);
    }
    @PostMapping("/api/staff/tables/{id}/regenerate-token") @Operation(summary="Revoke old QR and return new metadata (ADMIN)") @SecurityRequirement(name="bearerAuth")
    public TableDtos.Qr regenerate(@PathVariable Long id) { return service.regenerate(id); }
}
