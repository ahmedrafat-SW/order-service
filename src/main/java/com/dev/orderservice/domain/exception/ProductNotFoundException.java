package com.dev.orderservice.domain.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long productId) {
        super(String.format("Product not found with ID: %d", productId));
    }
}
