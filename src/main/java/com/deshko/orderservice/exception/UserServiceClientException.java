package com.deshko.orderservice.exception;

import lombok.Getter;

@Getter
public class UserServiceClientException extends RuntimeException {
    private final int status;

    public UserServiceClientException(int status) {
        super("User service rejected request, status " + status);
        this.status = status;
    }
}
