package com.deshko.orderservice.kafka;

import com.deshko.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {
    public static final String TOPIC = "create-payment";
    private final OrderService orderService;

    @KafkaListener(topics = TOPIC)
    public void handleCreatePayment(CreatePaymentEvent event) {
        log.info("Received CREATE_PAYMENT for order {}: {}", event.orderId(), event.status());
        orderService.applyPaymentResult(event.orderId(), event.status());
    }
}
