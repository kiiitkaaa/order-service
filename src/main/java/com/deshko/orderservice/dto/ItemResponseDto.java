package com.deshko.orderservice.dto;

import java.math.BigDecimal;

public record ItemResponseDto(Long id, String name, BigDecimal price) {}
