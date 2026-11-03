package com.deshko.orderservice.entity;

public enum OrderStatus {
    NEW,
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        if (this == target) {
            return true;
        }

        return switch (this) {
            case NEW -> target == PENDING_PAYMENT || target == CANCELLED;
            case PENDING_PAYMENT -> target == PAID || target == CANCELLED;
            case PAID -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
