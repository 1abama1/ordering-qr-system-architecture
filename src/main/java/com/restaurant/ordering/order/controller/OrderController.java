package com.restaurant.ordering.order.controller;
import com.restaurant.ordering.order.dto.*;
import com.restaurant.ordering.order.entity.OrderStatus;
import com.restaurant.ordering.order.service.OrderService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
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
@RestController @RequestMapping("/api/orders") @RequiredArgsConstructor @Validated @Tag(name="Orders")
public class OrderController {
    private final OrderService service;
    @PostMapping @Operation(summary="Create an order using a valid QR token", description="Server prices only. UUID Idempotency-Key is required. New order: 201; replay: 200. Duplicate IDs are rejected.")
    @ApiResponses({
        @ApiResponse(responseCode="201", description="New order persisted", content=@Content(schema=@Schema(implementation=OrderResponse.class))),
        @ApiResponse(responseCode="200", description="Idempotent replay", content=@Content(schema=@Schema(implementation=OrderResponse.class)))
    })
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest r,
        @Parameter(example="60a4c3b8-49f0-4e17-a95a-3c403c3f8806") @RequestHeader("Idempotency-Key") String key) {
        var result = service.create(r,key);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .location(URI.create("/api/orders/" + result.order().id())).header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(result.order());
    }
    @GetMapping("/active") @Operation(summary="Active KDS orders oldest first (ADMIN, KITCHEN)", description="Bounded pages, size 1..200. Load successive pages until an empty array.") @SecurityRequirement(name="bearerAuth")
    public List<OrderResponse> active(@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue="100") @Min(1) @Max(200) int size) { return service.active(page,size); }
    @GetMapping("/{id}") @Operation(summary="Order details and history (ADMIN, KITCHEN)") @SecurityRequirement(name="bearerAuth")
    public OrderResponse get(@PathVariable Long id) { return service.get(id); }
    @PatchMapping("/{id}/status") @Operation(summary="Change status using JSON body (ADMIN, KITCHEN)") @SecurityRequirement(name="bearerAuth")
    public OrderResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest r) { return service.status(id,r.status()); }
    @PatchMapping("/{id}/complete") @Operation(summary="Complete from PENDING or COOKING; repeat is safe (ADMIN, KITCHEN)") @SecurityRequirement(name="bearerAuth")
    public OrderResponse complete(@PathVariable Long id) { return service.status(id, OrderStatus.COMPLETED); }
}
