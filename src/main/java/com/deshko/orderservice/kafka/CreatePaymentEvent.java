package com.deshko.orderservice.kafka;

import com.deshko.orderservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record CreatePaymentEvent(
        String eventType,
        String paymentId,
        Long orderId,
        Long userId,
        PaymentStatus status,
        BigDecimal paymentAmount,
        Instant timestamp
) {}
