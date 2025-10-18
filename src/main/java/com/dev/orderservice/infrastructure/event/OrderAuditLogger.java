package com.dev.orderservice.infrastructure.event;

import com.dev.orderservice.domain.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderAuditLogger {
    private final Logger log = LoggerFactory.getLogger(OrderAuditLogger.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("=== AUDIT LOGGER ===");
        log.info("Logging order creation event for Order ID: {}", event.getOrderId());
        try {
            logOrderDetails(event);
            log.info("Audit log created successfully for Order ID: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to create audit log for Order ID: {}", event.getOrderId(), e);
        }
    }
    private void logOrderDetails(OrderCreatedEvent event) {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log.info("📝 AUDIT LOG ENTRY:");
        log.info("   Event: ORDER_CREATED");
        log.info("   Order ID: {}", event.getOrderId());
        log.info("   Customer ID: {}", event.getCustomerId());
        log.info("   Timestamp: {}", event.getTimestamp());
        log.info("   Total Amount: {}", event.getOrder().getTotalAmount());
        log.info("   Items Count: {}", event.getOrder().getItems().size());
        log.info("   Payment Method: {}", event.getOrder().getPayment().getPaymentMethod());
        log.info("   Status: {}", event.getOrder().getStatus());
        event.getOrder().getItems().forEach(item ->
            log.info("      - Product ID: {}, Quantity: {}, Price: {}", item.getProductId(), item.getQuantity(), item.getPrice())
        );
    }
}
