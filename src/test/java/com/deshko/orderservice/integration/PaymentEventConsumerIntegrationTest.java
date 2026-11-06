package com.deshko.orderservice.integration;

import com.deshko.orderservice.entity.Order;
import com.deshko.orderservice.entity.OrderStatus;
import com.deshko.orderservice.kafka.PaymentEventConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

class PaymentEventConsumerIntegrationTest extends AbstractIntegrationTest {
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void successEvent_marksNewOrderPaid() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        sendPaymentEvent(order.getId(), "SUCCESS");

        awaitStatus(order.getId(), OrderStatus.PAID);
    }

    @Test
    void failedEvent_movesNewOrderToPendingPayment() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        sendPaymentEvent(order.getId(), "FAILED");

        awaitStatus(order.getId(), OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void failedThenSuccess_endsPaid() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        sendPaymentEvent(order.getId(), "FAILED");
        awaitStatus(order.getId(), OrderStatus.PENDING_PAYMENT);

        sendPaymentEvent(order.getId(), "SUCCESS");
        awaitStatus(order.getId(), OrderStatus.PAID);
    }

    @Test
    void eventForPaidOrder_isIgnored() throws Exception {
        Order paid = saveOrder(1, OrderStatus.PAID, 1, 1);
        Order other = saveOrder(1, OrderStatus.NEW, 2, 1);

        sendPaymentEvent(paid.getId(), "FAILED");
        sendPaymentEvent(other.getId(), "SUCCESS");

        awaitStatus(other.getId(), OrderStatus.PAID);
        assertThat(statusOf(paid.getId())).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void eventForUnknownOrder_doesNotBreakConsumer() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        sendPaymentEvent(999_999L, "SUCCESS");
        sendPaymentEvent(order.getId(), "SUCCESS");

        awaitStatus(order.getId(), OrderStatus.PAID);
    }

    @Test
    void malformedMessage_doesNotBlockConsumer() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        kafkaTemplate.send(PaymentEventConsumer.TOPIC, "key", "this is not json")
                .get(10, TimeUnit.SECONDS);
        sendPaymentEvent(order.getId(), "SUCCESS");

        awaitStatus(order.getId(), OrderStatus.PAID);
    }

    @Test
    void eventWithForeignTypeHeader_isStillDeserialized() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        ProducerRecord<String, String> record = new ProducerRecord<>(PaymentEventConsumer.TOPIC,
                String.valueOf(order.getId()),
                "{\"orderId\": %d, \"status\": \"SUCCESS\"}".formatted(order.getId()));
        record.headers().add("__TypeId__",
                "com.deshko.paymentservice.dto.CreatePaymentEvent".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);

        awaitStatus(order.getId(), OrderStatus.PAID);
    }

    @Test
    void paymentResult_isVisibleThroughApi() throws Exception {
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        sendPaymentEvent(order.getId(), "SUCCESS");
        awaitStatus(order.getId(), OrderStatus.PAID);

        mockMvc.perform(get("/api/v1/orders/{id}", order.getId())
                        .with(asUser(1, "ivan@mail.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.user.name").value("Ivan"));
    }

    private void sendPaymentEvent(Long orderId, String status) throws Exception {
        String json = "{\"orderId\": %d, \"status\": \"%s\"}".formatted(orderId, status);
        kafkaTemplate.send(PaymentEventConsumer.TOPIC, String.valueOf(orderId), json)
                .get(10, TimeUnit.SECONDS);
    }

    private void awaitStatus(Long orderId, OrderStatus expected) {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(statusOf(orderId)).isEqualTo(expected));
    }

    private OrderStatus statusOf(Long orderId) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
        return OrderStatus.valueOf(status);
    }
}
