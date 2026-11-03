package com.deshko.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequestDto(
        @NotNull
        Long itemId,

        @NotNull
        @Positive
        Integer quantity
) {}
