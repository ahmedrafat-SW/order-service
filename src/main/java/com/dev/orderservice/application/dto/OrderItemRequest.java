package com.dev.orderservice.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest {
    @NotNull(message = "Product ID is required")
    @Positive(message = "Product ID must be positive")
    private Long productId;
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

}
