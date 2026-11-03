package com.deshko.orderservice.exception;

import com.deshko.orderservice.entity.OrderStatus;

public class OrderNotEditableException extends RuntimeException {
    public OrderNotEditableException(Long id, OrderStatus status) {
        super("Order " + id + " items cannot be changed in status " + status);
    }
}
