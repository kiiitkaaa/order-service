package com.deshko.orderservice.integration;

import com.deshko.orderservice.entity.OrderStatus;
import com.deshko.orderservice.entity.Order;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.MediaType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserServiceCircuitBreakerTest extends AbstractIntegrationTest {
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private CircuitBreaker breaker() {
        return circuitBreakerRegistry.circuitBreaker("userService");
    }

    private void getOrderExpectingPlaceholder(Order order) throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", order.getId())
                        .with(asUser(1L, "ivan@mail.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.name").isEmpty());
    }

    @Test
    void repeatedFailures_openCircuit_andUserServiceIsNoLongerCalled() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);
        stubUserByIdStatus(1, 500);

        for (int i = 0; i < 5; i++) {
            getOrderExpectingPlaceholder(order);
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        resetUserServiceRequests();
        getOrderExpectingPlaceholder(order);
        verifyNoUserServiceCalls();
    }

    @Test
    void openCircuit_makesCreateReturn503_withoutCallingUserService() throws Exception {
        breaker().transitionToOpenState();

        mockMvc.perform(post("/api/v1/orders")
                        .with(asUser(1L, "ivan@mail.com"))
                        .contentType(JSON)
                        .content("{\"items\": [{\"itemId\": 1, \"quantity\": 1}]}"))
                .andExpect(status().isServiceUnavailable());

        verifyNoUserServiceCalls();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void halfOpen_successfulCalls_closeCircuit() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        breaker().transitionToOpenState();
        breaker().transitionToHalfOpenState();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/orders/{id}", order.getId())
                            .with(asUser(1L, "ivan@mail.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.name").value("Ivan"));
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void businessErrors404_doNotOpenCircuit() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);
        stubUserByIdStatus(1, 404);

        for (int i = 0; i < 10; i++) {
            getOrderExpectingPlaceholder(order);
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void slowUserService_hitsReadTimeout_andFallsBack() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);
        stubUserByIdDelayed(1, "Ivan", "Petrov", "ivan@mail.com", 3500);

        getOrderExpectingPlaceholder(order);
    }
}
