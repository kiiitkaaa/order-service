package com.deshko.orderservice.exception;

import com.deshko.orderservice.entity.OrderStatus;

public class InvalidOrderStatusTransitionException extends RuntimeException {
    public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot change order status from " + from + " to " + to);
    }
}
