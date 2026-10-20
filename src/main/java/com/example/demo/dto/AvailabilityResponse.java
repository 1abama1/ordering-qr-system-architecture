package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Ответ стоп-листа по контракту: { "id": 1, "isAvailable": false } */
public record AvailabilityResponse(
        Long id,
        @JsonProperty("isAvailable") boolean isAvailable
) {
}
