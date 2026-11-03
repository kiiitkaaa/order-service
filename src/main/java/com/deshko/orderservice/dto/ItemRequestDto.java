package com.deshko.orderservice.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ItemRequestDto(
        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        @Positive
        @Digits(integer = 17, fraction = 2)
        BigDecimal price
) {}
