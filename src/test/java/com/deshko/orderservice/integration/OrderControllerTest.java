package com.deshko.orderservice.integration;

import com.deshko.orderservice.entity.Order;
import com.deshko.orderservice.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest extends AbstractIntegrationTest {
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void create_takesEmailFromHeader_returnsOrderWithUserWithoutCards() throws Exception {
        stubUserByEmail(1, "Ivan", "Petrov", "ivan@mail.com");

        mockMvc.perform(post("/api/v1/orders")
                        .with(asUser(1, "ivan@mail.com"))
                        .contentType(JSON)
                        .content("""
                                {"items": [{"itemId": 1, "quantity": 2}, {"itemId": 2, "quantity": 1}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/orders/\\d+")))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.totalPrice").value(119.70))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.user.email").value("ivan@mail.com"))
                .andExpect(jsonPath("$.user.cards").doesNotExist());

        verifyEmailLookup(1, "ivan@mail.com");
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void create_unknownItem_returns404AndSavesNothing() throws Exception {
        stubUserByEmail(1, "Ivan", "Petrov", "ivan@mail.com");

        mockMvc.perform(post("/api/v1/orders")
                        .with(asUser(1, "ivan@mail.com"))
                        .contentType(JSON)
                        .content("{\"items\": [{\"itemId\": 999, \"quantity\": 1}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("999")));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void create_userNotFoundInUserService_returns404() throws Exception {
        stubUserByEmailStatus(404);

        mockMvc.perform(post("/api/v1/orders")
                        .with(asUser(1, "ivan@mail.com"))
                        .contentType(JSON)
                        .content("{\"items\": [{\"itemId\": 1, \"quantity\": 1}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_userServiceDown_returns503() throws Exception {
        stubUserByEmailStatus(500);

        mockMvc.perform(post("/api/v1/orders")
                        .with(asUser(1, "ivan@mail.com"))
                        .contentType(JSON)
                        .content("{\"items\": [{\"itemId\": 1, \"quantity\": 1}]}"))
                .andExpect(status().isServiceUnavailable());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(asUser(1, "ivan@mail.com"))
                        .contentType(JSON)
                        .content("{\"items\": [{\"itemId\": 1, \"quantity\": 0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['items[0].quantity']").exists());

        verifyNoUserServiceCalls();
    }

    @Test
    void anyEndpoint_withoutGatewayHeaders_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void getById_owner_seesOrderWithUser() throws Exception {
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(get("/api/v1/orders/{id}",
                        order.getId()).with(asUser(1, "ivan@mail.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.items[0].item.name").value("Keyboard"))
                .andExpect(jsonPath("$.user.name").value("Ivan"));
    }

    @Test
    void getById_anotherUser_returns403() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(get("/api/v1/orders/{id}",
                        order.getId()).with(asUser(2, "anna@mail.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_adminUnknownOrder_returns404_butUserGets403() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", 12345).with(asAdmin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/orders/{id}", 12345).with(
                asUser(1, "ivan@mail.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMine_returnsOnlyOwnOrders() throws Exception {
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        saveOrder(1, OrderStatus.NEW, 1, 1);
        saveOrder(2, OrderStatus.NEW, 2, 1);

        mockMvc.perform(get("/api/v1/orders/me").with(asUser(1, "ivan@mail.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.id").value(1));
    }

    @Test
    void getByUserId_adminOrSelfOnly() throws Exception {
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(get("/api/v1/orders/user/{id}", 1).with(asAdmin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/orders/user/{id}", 1).with(
                asUser(2, "anna@mail.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAll_admin_filtersByStatusAndDateAndPaginates() throws Exception {
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        stubUserById(2, "Anna", "Ivanova", "anna@mail.com");
        saveOrder(1, OrderStatus.NEW, 1, 1);
        saveOrder(1, OrderStatus.PAID, 2, 1);
        saveOrder(2, OrderStatus.NEW, 3, 1);

        mockMvc.perform(get("/api/v1/orders").with(asAdmin())
                        .param("statuses", "NEW").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/orders").with(asAdmin())
                        .param("statuses", "NEW,PAID"))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/v1/orders").with(asAdmin())
                        .param("from", "2999-01-01T00:00:00"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/orders").with(asAdmin())
                        .param("to", "2999-01-01T00:00:00"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void getAll_validationAndAccess() throws Exception {
        mockMvc.perform(get("/api/v1/orders").with(asUser(1, "ivan@mail.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders").with(asAdmin()).param("statuses", "NOPE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/orders").with(asAdmin())
                        .param("from", "2026-12-01T00:00:00")
                        .param("to", "2026-01-01T00:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_adminChangesStatus() throws Exception {
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(patch("/api/v1/orders/{id}", order.getId()).with(asAdmin())
                        .contentType(JSON).content("{\"status\": \"PENDING_PAYMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void update_ownerCanOnlyCancel() throws Exception {
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(patch("/api/v1/orders/{id}", order.getId()).with(
                asUser(1, "ivan@mail.com"))
                        .contentType(JSON).content("{\"status\": \"PAID\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/orders/{id}", order.getId()).with(
                asUser(1, "ivan@mail.com"))
                        .contentType(JSON).content("{\"status\": \"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void update_replacesItemsAndRecalculatesTotal() throws Exception {
        stubUserById(1, "Ivan", "Petrov", "ivan@mail.com");
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(patch("/api/v1/orders/{id}", order.getId()).with(
                asUser(1, "ivan@mail.com"))
                        .contentType(JSON)
                        .content("{\"items\": [{\"itemId\": 3, \"quantity\": 2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].item.id").value(3))
                .andExpect(jsonPath("$.totalPrice").value(378.00));

        Integer lines = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, order.getId());
        assertThat(lines).isEqualTo(1);
    }

    @Test
    void update_itemsOfPaidOrder_returns409() throws Exception {
        Order order = saveOrder(1, OrderStatus.PAID, 1, 1);

        mockMvc.perform(patch("/api/v1/orders/{id}", order.getId()).with(asAdmin())
                        .contentType(JSON)
                        .content("{\"items\": [{\"itemId\": 2, \"quantity\": 1}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_invalidTransition_returns409() throws Exception {
        Order order = saveOrder(1, OrderStatus.PAID, 1, 1);

        mockMvc.perform(patch("/api/v1/orders/{id}", order.getId()).with(asAdmin())
                        .contentType(JSON).content("{\"status\": \"NEW\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_paidOrderCannotBeCancelled_returns409() throws Exception {
        Order order = saveOrder(1, OrderStatus.PAID, 1, 1);

        mockMvc.perform(patch("/api/v1/orders/{id}", order.getId()).with(asAdmin())
                        .contentType(JSON).content("{\"status\": \"CANCELLED\"}"))
                .andExpect(status().isConflict());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void update_ownerCannotCancelPaidOrder_returns409() throws Exception {
        Order order = saveOrder(1, OrderStatus.PAID, 1, 1);

        mockMvc.perform(patch("/api/v1/orders/{id}", order.getId())
                        .with(asUser(1, "ivan@mail.com"))
                        .contentType(JSON).content("{\"status\": \"CANCELLED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_owner_softDeletesOrder() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(delete("/api/v1/orders/{id}", order.getId()).with(
                asUser(1, "ivan@mail.com")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/orders/{id}", order.getId()).with(asAdmin()))
                .andExpect(status().isNotFound());
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM orders WHERE id = ?", Boolean.class, order.getId());
        assertThat(deleted).isTrue();
    }

    @Test
    void delete_anotherUser_returns403AndKeepsOrder() throws Exception {
        Order order = saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(delete("/api/v1/orders/{id}", order.getId()).with(
                asUser(2, "anna@mail.com")))
                .andExpect(status().isForbidden());

        assertThat(orderRepository.findById(order.getId())).isPresent();
    }
}