package com.dev.orderservice.domain.exception;

public class PaymentFailedException extends RuntimeException {
    private final Long orderId;
    private final String reason;
    public PaymentFailedException(Long orderId, String reason) {
        super(String.format("Payment failed for order ID %d. Reason: %s", orderId, reason));
        this.orderId = orderId;
        this.reason = reason;
    }
    public PaymentFailedException(String message) {
        super(message);
        this.orderId = null;
        this.reason = message;
    }
    public Long getOrderId() { return orderId; }
    public String getReason() { return reason; }
}
