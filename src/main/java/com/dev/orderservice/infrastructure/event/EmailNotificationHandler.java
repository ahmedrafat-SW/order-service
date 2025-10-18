package com.dev.orderservice.infrastructure.event;

import com.dev.orderservice.application.service.OrderApplicationService;
import com.dev.orderservice.domain.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailNotificationHandler {
    private final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);


    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("=== EMAIL NOTIFICATION HANDLER ===");
        log.info("Processing OrderCreatedEvent for Order ID: {}", event.getOrderId());
        try {
            sendOrderConfirmationEmail(event);
            log.info("Order confirmation email sent successfully to customer: {}", event.getCustomerId());
        } catch (Exception e) {
            log.error("Failed to send order confirmation email for Order ID: {}", event.getOrderId(), e);
        }
    }
    private void sendOrderConfirmationEmail(OrderCreatedEvent event) {
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log.info("📧 MOCK EMAIL SENT:");
        log.info("   To: customer-{}@example.com", event.getCustomerId());
        log.info("   Subject: Order Confirmation - Order #{}", event.getOrderId());
        log.info("   Body: Your order has been confirmed and is being processed.");
        log.info("   Order ID: {}", event.getOrderId());
        log.info("   Order Date: {}", event.getTimestamp());
    }
}
