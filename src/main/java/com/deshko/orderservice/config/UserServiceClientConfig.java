package com.deshko.orderservice.config;

import com.deshko.orderservice.security.GatewayUser;
import com.deshko.orderservice.filter.GatewayHeaderAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class UserServiceClientConfig {
    @Bean
    RestClient userServiceRestClient(@Value("${user-service.url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    if (SecurityContextHolder.getContext().getAuthentication() != null
                            && SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                            instanceof GatewayUser(Long id, String email, String role)
                    ) {
                        request.getHeaders().set(GatewayHeaderAuthenticationFilter.USER_ID, id.toString());
                        request.getHeaders().set(GatewayHeaderAuthenticationFilter.USER_ROLE, role);
                        request.getHeaders().set(GatewayHeaderAuthenticationFilter.USER_EMAIL, email);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
