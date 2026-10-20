package com.restaurant.ordering.table.dto;
import com.restaurant.ordering.table.entity.RestaurantTable;
import jakarta.validation.constraints.*;
public final class TableDtos {
    private TableDtos() {}
    public record Create(@NotNull @Positive @Max(10000) Integer number) {}
    public record Update(@NotNull Boolean isActive) {}
    public record Response(Long id, int number, boolean isActive, int qrTokenVersion) {
        public static Response from(RestaurantTable t) { return new Response(t.getId(), t.getNumber(), t.isActive(), t.getQrTokenVersion()); }
    }
    public record Qr(String url, String tableToken, int tableNumber, int qrTokenVersion) {}
}
