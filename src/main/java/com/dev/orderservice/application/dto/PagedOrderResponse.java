package com.dev.orderservice.application.dto;

import lombok.*;

import java.util.List;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedOrderResponse {
    private List<OrderResponse> orders;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private int size;
}
