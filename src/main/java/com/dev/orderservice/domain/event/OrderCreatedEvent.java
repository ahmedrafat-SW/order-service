package com.dev.orderservice.domain.event;

import com.dev.orderservice.domain.model.Order;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class OrderCreatedEvent {
    private final Long orderId;
    private final Long customerId;
    private final LocalDateTime timestamp;
    private final Order order;
    public OrderCreatedEvent(Order order) {
        this.orderId = order.getId();
        this.customerId = order.getCustomerId();
        this.timestamp = LocalDateTime.now();
        this.order = order;
    }
}
