package com.deshko.orderservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainerConfig {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));
    }

    @Bean
    DynamicPropertyRegistrar kafkaTestProperties() {
        return registry -> {
            registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
            registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
            registry.add("spring.kafka.consumer.properties.spring.json.use.type.headers", () -> "false");
            registry.add("spring.kafka.consumer.properties.spring.json.value.default.type",
                    () -> "com.deshko.orderservice.kafka.CreatePaymentEvent");
        };
    }
}
