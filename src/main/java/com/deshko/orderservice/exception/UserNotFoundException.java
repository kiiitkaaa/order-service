package com.deshko.orderservice.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String key) {
        super("User not found: " + key);
    }
}
