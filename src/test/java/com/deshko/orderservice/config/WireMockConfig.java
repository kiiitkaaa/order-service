package com.deshko.orderservice.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class WireMockConfig {
    @Bean(initMethod = "start", destroyMethod = "stop")
    WireMockServer userServiceWireMock() {
        return new WireMockServer(WireMockConfiguration.options().dynamicPort());
    }

    @Bean
    DynamicPropertyRegistrar userServiceUrlRegistrar(WireMockServer userServiceWireMock) {
        return registry -> registry.add("user-service.url", userServiceWireMock::baseUrl);
    }
}