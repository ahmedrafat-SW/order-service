package com.dev.orderservice.domain.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super(String.format("Order not found with ID: %d", orderId));
    }
}
