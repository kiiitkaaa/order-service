package com.deshko.orderservice.dto;

import com.deshko.orderservice.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseDto(
        Long id,
        OrderStatus status,
        BigDecimal totalPrice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderItemResponseDto> items,
        UserInfoDto user) {
    public OrderResponseDto withUser(UserInfoDto user) {
        return new OrderResponseDto(id, status, totalPrice, createdAt, updatedAt, items, user);
    }
}
