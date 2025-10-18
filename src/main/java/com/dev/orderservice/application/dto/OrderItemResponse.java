package com.dev.orderservice.application.dto;

import lombok.*;

import java.math.BigDecimal;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subtotal;
}
