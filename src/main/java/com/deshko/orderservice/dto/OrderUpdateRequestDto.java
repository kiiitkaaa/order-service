package com.deshko.orderservice.dto;

import com.deshko.orderservice.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderUpdateRequestDto(
        OrderStatus status,

        @Size(min = 1)
        List<@Valid OrderItemRequestDto> items
) {}
