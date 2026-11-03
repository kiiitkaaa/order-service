package com.deshko.orderservice.client;

import com.deshko.orderservice.dto.UserInfoDto;
import com.deshko.orderservice.exception.UserNotFoundException;
import com.deshko.orderservice.exception.UserServiceClientException;
import com.deshko.orderservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {
    private final RestClient userServiceRestClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "getByEmailFallback")
    public UserInfoDto getByEmail(String email) {
        return userServiceRestClient.get()
                .uri("/api/v1/users/email/{email}", email)
                .retrieve()
                .onStatus(status -> status.value() == 404, (_, _) -> {
                    throw new UserNotFoundException(email);
                })
                .body(UserInfoDto.class);
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "getByIdFallback")
    public UserInfoDto getById(Long id) {
        return userServiceRestClient.get()
                .uri("/api/v1/users/{id}", id)
                .retrieve()
                .onStatus(status -> status.value() == 404, (_, _) -> {
                    throw new UserNotFoundException(String.valueOf(id));
                })
                .onStatus(HttpStatusCode::is4xxClientError, (_, res) -> {
                    throw new UserServiceClientException(res.getStatusCode().value());
                })
                .body(UserInfoDto.class);
    }

    private UserInfoDto getByEmailFallback(String email, Throwable ex) {
        rethrowBusinessException(ex);
        log.warn("User service unavailable during email lookup: {}", ex.toString());
        throw new UserServiceUnavailableException(ex);
    }

    private UserInfoDto getByIdFallback(Long id, Throwable ex) {
        rethrowBusinessException(ex);
        log.warn("User service unavailable for user {}: {}", id, ex.toString());
        return UserInfoDto.unavailable(id);
    }

    private static void rethrowBusinessException(Throwable ex) {
        if (ex instanceof UserNotFoundException || ex instanceof UserServiceClientException) {
            throw (RuntimeException) ex;
        }
    }
}
