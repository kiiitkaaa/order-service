package com.deshko.orderservice.entity;

public enum OrderStatus {
    NEW,
    PENDING_PAYMENT,
    PAID,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        if (this == target) {
            return true;
        }

        return switch (this) {
            case NEW -> target == PENDING_PAYMENT || target == CANCELLED;
            case PENDING_PAYMENT -> target == PAID || target == CANCELLED;
            case PAID, CANCELLED -> false;
        };
    }
}