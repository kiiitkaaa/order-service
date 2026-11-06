package com.deshko.orderservice.kafka;

import com.deshko.orderservice.entity.PaymentStatus;
import com.deshko.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {
    @Mock private OrderService orderService;
    @InjectMocks private PaymentEventConsumer consumer;

    private static CreatePaymentEvent event(Long orderId, PaymentStatus status) {
        return new CreatePaymentEvent("PAYMENT_CREATED", "pay-" + orderId, orderId, 42L,
                status, new BigDecimal("49.90"), Instant.parse("2026-11-05T10:00:00Z")
        );
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void handleCreatePayment_passesOrderIdAndStatusToService(PaymentStatus status) {
        consumer.handleCreatePayment(event(5L, status));

        verify(orderService).applyPaymentResult(5L, status);
    }

    @Test
    void handleCreatePayment_serviceFailure_isNotSwallowed() {
        doThrow(new IllegalStateException("db down"))
                .when(orderService).applyPaymentResult(5L, PaymentStatus.SUCCESS);

        assertThatThrownBy(() -> consumer.handleCreatePayment(event(5L, PaymentStatus.SUCCESS)))
                .isInstanceOf(IllegalStateException.class);
    }
}