package com.deshko.orderservice.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class OrderStatusTest {
    @ParameterizedTest
    @CsvSource({
            "NEW,PENDING_PAYMENT,true", "NEW,PAID,false", "NEW,CANCELLED,true",
            "PAID,PROCESSING,true", "PAID,CANCELLED,true", "PAID,PAID,true",
            "SHIPPED,DELIVERED,true", "SHIPPED,CANCELLED,false",
            "DELIVERED,NEW,false", "CANCELLED,NEW,false"})
    void canTransitionTo(OrderStatus from, OrderStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }
}
