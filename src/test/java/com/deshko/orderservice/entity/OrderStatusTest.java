package com.deshko.orderservice.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class OrderStatusTest {
    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "NEW, NEW, true",
            "NEW, PENDING_PAYMENT, true",
            "NEW, PAID, false",
            "NEW, CANCELLED, true",
            "PENDING_PAYMENT, NEW, false",
            "PENDING_PAYMENT, PENDING_PAYMENT, true",
            "PENDING_PAYMENT, PAID, true",
            "PENDING_PAYMENT, CANCELLED, true",
            "PAID, NEW, false",
            "PAID, PENDING_PAYMENT, false",
            "PAID, PAID, true",
            "PAID, CANCELLED, false",
            "CANCELLED, NEW, false",
            "CANCELLED, PENDING_PAYMENT, false",
            "CANCELLED, PAID, false",
            "CANCELLED, CANCELLED, true"
    })
    void canTransitionTo(OrderStatus from, OrderStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }
}
