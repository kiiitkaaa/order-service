package com.deshko.orderservice.exception;

public class ItemInUseException extends RuntimeException {
    public ItemInUseException(Long id) {
        super("Item " + id + " is used in orders and cannot be deleted");
    }
}
