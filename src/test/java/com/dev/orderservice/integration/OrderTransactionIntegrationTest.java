package com.dev.orderservice.integration;

import com.dev.orderservice.application.dto.*;
import com.dev.orderservice.application.service.OrderApplicationService;
import com.dev.orderservice.domain.exception.InsufficientStockException;
import com.dev.orderservice.domain.model.Order;
import com.dev.orderservice.domain.model.Product;
import com.dev.orderservice.domain.repository.OrderRepository;
import com.dev.orderservice.domain.repository.ProductRepository;
import com.dev.orderservice.domain.valueobject.OrderStatus;
import com.dev.orderservice.domain.valueobject.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderTransactionIntegrationTest {

    @Autowired
    private OrderApplicationService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        // Create test products
        product1 = Product.builder()
                .name("Product 1")
                .price(new BigDecimal("100.00"))
                .stockQuantity(100)
                .build();
        product1 = productRepository.save(product1);

        product2 = Product.builder()
                .name("Product 2")
                .price(new BigDecimal("200.00"))
                .stockQuantity(50)
                .build();
        product2 = productRepository.save(product2);
    }

    @Test
    void testCreateOrderTransaction_Success() {
        // Arrange
        OrderItemRequest item = OrderItemRequest.builder()
                .productId(product1.getId())
                .quantity(5)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("500.00"));

        // Verify order persisted
        Order savedOrder = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(savedOrder.getItems()).hasSize(1);
        assertThat(savedOrder.getPayment()).isNotNull();

        // Verify stock updated
        Product updatedProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(95);
    }

    @Test
    void testCreateOrderTransaction_RollbackOnStockError() {
        // Arrange
        int initialStock = product1.getStockQuantity();

        OrderItemRequest item = OrderItemRequest.builder()
                .productId(product1.getId())
                .quantity(200) // More than available
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class);

        // Verify rollback - stock should not change
        Product product = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(initialStock);

        // Verify no order created
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).isEmpty();
    }

    @Test
    void testCreateOrderTransaction_MultipleItems() {
        // Arrange
        OrderItemRequest item1 = OrderItemRequest.builder()
                .productId(product1.getId())
                .quantity(10)
                .build();

        OrderItemRequest item2 = OrderItemRequest.builder()
                .productId(product2.getId())
                .quantity(5)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(Arrays.asList(item1, item2))
                .paymentMethod(PaymentMethod.PAYPAL)
                .build();

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("2000.00")); // 1000 + 1000
        assertThat(response.getItems()).hasSize(2);

        // Verify both products' stock updated
        Product updatedProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        Product updatedProduct2 = productRepository.findById(product2.getId()).orElseThrow();
        
        assertThat(updatedProduct1.getStockQuantity()).isEqualTo(90);
        assertThat(updatedProduct2.getStockQuantity()).isEqualTo(45);
    }

    @Test
    void testConcurrentOrderCreation_RaceConditionPrevention() throws InterruptedException {
        // Arrange
        int numberOfThreads = 10;
        int quantityPerOrder = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Set product stock to limited amount
        product1.setStockQuantity(30); // Only enough for 6 orders of 5 items each
        productRepository.save(product1);

        // Act - Multiple threads trying to order simultaneously
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    OrderItemRequest item = OrderItemRequest.builder()
                            .productId(product1.getId())
                            .quantity(quantityPerOrder)
                            .build();

                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .customerId(1L)
                            .items(List.of(item))
                            .paymentMethod(PaymentMethod.CREDIT_CARD)
                            .build();

                    orderService.createOrder(request);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Assert
        // Should have 6 successful orders and 4 failures
        assertThat(successCount.get()).isEqualTo(6);
        assertThat(failureCount.get()).isEqualTo(4);

        // Verify final stock is 0
        Product finalProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(finalProduct.getStockQuantity()).isEqualTo(0);

        // Verify correct number of orders created
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).hasSize(6);
    }

    @Test
    void testPagination() {
        // Arrange - Create multiple orders
        for (int i = 0; i < 25; i++) {
            OrderItemRequest item = OrderItemRequest.builder()
                    .productId(product1.getId())
                    .quantity(1)
                    .build();

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .customerId((long) i)
                    .items(List.of(item))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .build();

            orderService.createOrder(request);
        }

        // Act
        PagedOrderResponse page1 = orderService.getOrders(PageRequest.of(0, 10));
        PagedOrderResponse page2 = orderService.getOrders(PageRequest.of(1, 10));
        PagedOrderResponse page3 = orderService.getOrders(PageRequest.of(2, 10));

        // Assert
        assertThat(page1.getOrders()).hasSize(10);
        assertThat(page1.getCurrentPage()).isEqualTo(0);
        assertThat(page1.getTotalElements()).isEqualTo(25);
        assertThat(page1.getTotalPages()).isEqualTo(3);

        assertThat(page2.getOrders()).hasSize(10);
        assertThat(page3.getOrders()).hasSize(5);
    }

    @Test
    void testHighValueOrdersQuery() {
        // Arrange - Create orders with different amounts
        createOrderWithAmount(product1, 5, new BigDecimal("500.00"));    // Not high value
        createOrderWithAmount(product1, 15, new BigDecimal("1500.00"));  // High value
        createOrderWithAmount(product2, 6, new BigDecimal("1200.00"));   // High value
        createOrderWithAmount(product1, 3, new BigDecimal("300.00"));    // Not high value

        // Act
        List<OrderResponse> highValueOrders = orderService.getHighValueOrders();

        // Assert
        assertThat(highValueOrders).hasSize(2);
        assertThat(highValueOrders)
                .allMatch(order -> order.getTotalAmount().compareTo(new BigDecimal("1000")) > 0);
    }

    @Test
    void testHighValueOrdersQuery_WithCustomMinAmount() {
        // Arrange
        createOrderWithAmount(product1, 5, new BigDecimal("500.00"));
        createOrderWithAmount(product1, 15, new BigDecimal("1500.00"));
        createOrderWithAmount(product2, 10, new BigDecimal("2000.00"));

        // Act
        List<OrderResponse> orders = orderService.getHighValueOrders(new BigDecimal("1500"));

        // Assert
        assertThat(orders).hasSize(2); // 1500 and 2000
    }

    @Test
    void testAsyncEventPublishing() throws InterruptedException {
        // Arrange
        OrderItemRequest item = OrderItemRequest.builder()
                .productId(product1.getId())
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Wait for async events to process
        Thread.sleep(500);

        // Assert - Order should be created successfully
        // Event handlers log messages - check logs manually or use log capturing
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    // Helper method
    private void createOrderWithAmount(Product product, int quantity, BigDecimal expectedTotal) {
        // Adjust product price to match expected total
        BigDecimal pricePerItem = expectedTotal.divide(BigDecimal.valueOf(quantity), 2, BigDecimal.ROUND_HALF_UP);
        product.setPrice(pricePerItem);
        productRepository.save(product);

        OrderItemRequest item = OrderItemRequest.builder()
                .productId(product.getId())
                .quantity(quantity)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        orderService.createOrder(request);
    }
}