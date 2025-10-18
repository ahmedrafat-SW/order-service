package com.dev.orderservice.application.dto;

import com.dev.orderservice.domain.valueobject.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;


@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    private Long customerId;
    @NotEmpty(message = "Order items cannot be empty")
    @Valid
    private List<OrderItemRequest> items;
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

}
