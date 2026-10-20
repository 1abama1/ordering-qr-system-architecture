package com.restaurant.ordering.order.dto;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;
public record CreateOrderRequest(
    @Schema(example="3") @NotNull @Positive Integer tableNumber,
    @Schema(description="Opaque token from the table QR URL", example="<signed-table-token>") @NotBlank @Size(max=256) String tableToken,
    @NotEmpty @Size(max=50) List<@NotNull @Valid Item> items) {
    public record Item(@Schema(example="1") @NotNull @Positive Long menuItemId, @Schema(example="2") @NotNull @Min(1) @Max(100) Integer quantity) {}
}
