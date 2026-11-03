package com.deshko.orderservice.integration;

import com.deshko.orderservice.config.TestContainerConfig;
import com.deshko.orderservice.config.WireMockConfig;
import com.deshko.orderservice.entity.Item;
import com.deshko.orderservice.entity.Order;
import com.deshko.orderservice.entity.OrderItem;
import com.deshko.orderservice.entity.OrderStatus;
import com.deshko.orderservice.repository.ItemRepository;
import com.deshko.orderservice.repository.OrderRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.math.BigDecimal;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestContainerConfig.class, WireMockConfig.class})
public abstract class AbstractIntegrationTest {
    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected OrderRepository orderRepository;
    @Autowired protected ItemRepository itemRepository;
    @Autowired protected CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired protected WireMockServer userService;

    @BeforeEach
    void resetState() {
        userService.resetAll();
        circuitBreakerRegistry.circuitBreaker("userService").reset();
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, items RESTART IDENTITY CASCADE");

        saveItem("Keyboard", "49.90");
        saveItem("Mouse", "19.90");
        saveItem("Monitor", "189.00");
    }

    private void saveItem(String name, String price) {
        Item item = new Item();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        itemRepository.save(item);
    }

    protected static RequestPostProcessor asUser(long id, String email) {
        return withHeaders(id, "USER", email);
    }

    protected static RequestPostProcessor asAdmin() {
        return withHeaders(99, "ADMIN", "admin@mail.com");
    }

    private static RequestPostProcessor withHeaders(long id, String role, String email) {
        return request -> {
            request.addHeader("X-User-Id", id);
            request.addHeader("X-User-Role", role);
            request.addHeader("X-User-Email", email);
            return request;
        };
    }

    protected void stubUserById(long id, String name, String surname, String email) {
        userService.stubFor(WireMock.get(urlPathEqualTo("/api/v1/users/" + id))
                .willReturn(okJson(userJson(id, name, surname, email))));
    }

    protected void stubUserByIdStatus(long id, int status) {
        userService.stubFor(WireMock.get(urlPathEqualTo("/api/v1/users/" + id))
                .willReturn(aResponse().withStatus(status)));
    }

    protected void stubUserByIdDelayed(long id, String name, String surname, String email, int delayMs) {
        userService.stubFor(WireMock.get(urlPathEqualTo("/api/v1/users/" + id))
                .willReturn(okJson(userJson(id, name, surname, email)).withFixedDelay(delayMs)));
    }

    protected void stubUserByEmail(long id, String name, String surname, String email) {
        userService.stubFor(WireMock.get(urlPathMatching("/api/v1/users/email/.+"))
                .willReturn(okJson(userJson(id, name, surname, email))));
    }

    protected void stubUserByEmailStatus(int status) {
        userService.stubFor(WireMock.get(urlPathMatching("/api/v1/users/email/.+"))
                .willReturn(aResponse().withStatus(status)));
    }

    protected void verifyEmailLookup(long callerId, String callerEmail) {
        userService.verify(1, getRequestedFor(urlPathMatching("/api/v1/users/email/.+"))
                .withHeader("X-User-Id", WireMock.equalTo(String.valueOf(callerId)))
                .withHeader("X-User-Email", WireMock.equalTo(callerEmail)));
    }

    protected void verifyNoUserServiceCalls() {
        userService.verify(0, anyRequestedFor(anyUrl()));
    }

    protected void resetUserServiceRequests() {
        userService.resetRequests();
    }

    private static String userJson(long id, String name, String surname, String email) {
        return """
                {"id": %d, "name": "%s", "surname": "%s", "birthDate": "1995-05-20", "email": "%s",
                 "active": true, "createdAt": "2026-10-01T10:00:00", "updatedAt": "2026-10-01T10:00:00",
                 "cards": []}
                """.formatted(id, name, surname, email);
    }

    protected Order saveOrder(long userId, OrderStatus status, long itemId, int quantity) {
        Item item = itemRepository.findById(itemId).orElseThrow();
        OrderItem line = new OrderItem();
        line.setItem(item);
        line.setQuantity(quantity);

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(status);
        order.addItem(line);
        order.setTotalPrice(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return orderRepository.save(order);
    }
}