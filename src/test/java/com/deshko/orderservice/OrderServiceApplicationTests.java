package com.deshko.orderservice;

import com.deshko.orderservice.config.TestContainerConfig;
import com.deshko.orderservice.config.WireMockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestContainerConfig.class, WireMockConfig.class})
class OrderServiceApplicationTests {
    @Test
    void contextLoads() {}
}
