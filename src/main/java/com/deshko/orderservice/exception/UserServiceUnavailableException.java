package com.deshko.orderservice.exception;

public class UserServiceUnavailableException extends RuntimeException {
    public UserServiceUnavailableException(Throwable cause) {
        super("User service is unavailable", cause);
    }
}
