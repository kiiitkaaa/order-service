package com.deshko.orderservice.dto;

public record OrderItemResponseDto(Long id, ItemResponseDto item, int quantity) {}
