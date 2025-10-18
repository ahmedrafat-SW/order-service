package com.dev.orderservice.application.dto;

import com.dev.orderservice.domain.valueobject.PaymentMethod;
import com.dev.orderservice.domain.valueobject.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
}
