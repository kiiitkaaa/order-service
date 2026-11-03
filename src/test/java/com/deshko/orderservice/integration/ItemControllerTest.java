package com.deshko.orderservice.integration;

import com.deshko.orderservice.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ItemControllerTest extends AbstractIntegrationTest {
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void create_admin_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/items").with(asAdmin()).contentType(JSON)
                        .content("{\"name\": \"Headset\", \"price\": 59.90}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/items/\\d+")))
                .andExpect(jsonPath("$.name").value("Headset"))
                .andExpect(jsonPath("$.price").value(59.90));

        assertThat(itemRepository.count()).isEqualTo(4);
    }

    @Test
    void create_user_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/items").with(asUser(1, "ivan@mail.com"))
                        .contentType(JSON)
                        .content("{\"name\": \"Headset\", \"price\": 59.90}"))
                .andExpect(status().isForbidden());

        assertThat(itemRepository.count()).isEqualTo(3);
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/items").with(asAdmin()).contentType(JSON)
                        .content("{\"name\": \" \", \"price\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasKey("name")))
                .andExpect(jsonPath("$.details", hasKey("price")))
                .andExpect(jsonPath("$.details.name").value("must not be blank"))
                .andExpect(jsonPath("$.details.price").value("must be greater than 0"));
    }

    @Test
    void getById_userAndAdmin_getItem() throws Exception {
        mockMvc.perform(get("/api/v1/items/{id}", 1)
                        .with(asUser(1, "ivan@mail.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard"));
        mockMvc.perform(get("/api/v1/items/{id}", 1).with(asAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void getById_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/items/{id}", 999).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_admin_changesItem_user_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/items/{id}", 1).with(asAdmin()).contentType(JSON)
                        .content("{\"name\": \"Keyboard Pro\", \"price\": 79.90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard Pro"));
        assertThat(itemRepository.findById(1L).orElseThrow().getPrice()).isEqualByComparingTo("79.90");

        mockMvc.perform(put("/api/v1/items/{id}", 1)
                        .with(asUser(1, "ivan@mail.com")).contentType(JSON)
                        .content("{\"name\": \"Hacked\", \"price\": 0.01}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unusedItem_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/items/{id}", 3).with(asAdmin()))
                .andExpect(status().isNoContent());

        assertThat(itemRepository.findById(3L)).isEmpty();
    }

    @Test
    void delete_itemUsedInOrder_returns409() throws Exception {
        saveOrder(1, OrderStatus.NEW, 1, 1);

        mockMvc.perform(delete("/api/v1/items/{id}", 1).with(asAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("orders")));

        assertThat(itemRepository.findById(1L)).isPresent();
    }

    @Test
    void delete_user_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/items/{id}", 3)
                        .with(asUser(1, "ivan@mail.com")))
                .andExpect(status().isForbidden());
    }
}
