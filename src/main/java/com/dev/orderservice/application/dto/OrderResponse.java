package com.dev.orderservice.application.dto;

import com.dev.orderservice.domain.valueobject.OrderStatus;
import com.dev.orderservice.domain.valueobject.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private Long customerId;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;
    private PaymentResponse payment;
}
