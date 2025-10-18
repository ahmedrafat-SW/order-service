package com.dev.orderservice.service;

import com.dev.orderservice.application.dto.*;
import com.dev.orderservice.application.service.OrderApplicationService;
import com.dev.orderservice.domain.event.OrderCreatedEvent;
import com.dev.orderservice.domain.exception.InsufficientStockException;
import com.dev.orderservice.domain.exception.ProductNotFoundException;
import com.dev.orderservice.domain.model.*;
import com.dev.orderservice.domain.repository.OrderRepository;
import com.dev.orderservice.domain.repository.PaymentRepository;
import com.dev.orderservice.domain.repository.ProductRepository;
import com.dev.orderservice.domain.valueobject.OrderStatus;
import com.dev.orderservice.domain.valueobject.PaymentMethod;
import com.dev.orderservice.domain.valueobject.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderApplicationService orderService;

    private Product testProduct;
    private CreateOrderRequest createOrderRequest;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .price(new BigDecimal("100.00"))
                .stockQuantity(10)
                .build();

        OrderItemRequest itemRequest = OrderItemRequest.builder()
                .productId(1L)
                .quantity(2)
                .build();

        createOrderRequest = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(itemRequest))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();
    }

    @Test
    void testCreateOrder_Success() {
        // Arrange
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testProduct));
        
        Order savedOrder = createMockOrder();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse response = orderService.createOrder(createOrderRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerId()).isEqualTo(1L);
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("200.00"));
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).save(testProduct);
        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
        
        // Verify stock was decremented
        assertThat(testProduct.getStockQuantity()).isEqualTo(8);
    }

    @Test
    void testCreateOrder_InsufficientStock() {
        // Arrange
        testProduct.setStockQuantity(1); // Less than requested
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testProduct));

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(createOrderRequest))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        verify(productRepository).findByIdForUpdate(1L);
        verify(orderRepository, never()).save(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void testCreateOrder_ProductNotFound() {
        // Arrange
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(createOrderRequest))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(productRepository).findByIdForUpdate(1L);
        verify(orderRepository, never()).save(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void testCreateOrder_MultipleItems() {
        // Arrange
        Product product2 = Product.builder()
                .id(2L)
                .name("Product 2")
                .price(new BigDecimal("50.00"))
                .stockQuantity(20)
                .build();

        OrderItemRequest item1 = OrderItemRequest.builder()
                .productId(1L)
                .quantity(2)
                .build();

        OrderItemRequest item2 = OrderItemRequest.builder()
                .productId(2L)
                .quantity(3)
                .build();

        CreateOrderRequest multiItemRequest = CreateOrderRequest.builder()
                .customerId(1L)
                .items(Arrays.asList(item1, item2))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(product2));

        Order savedOrder = createMockOrderWithMultipleItems();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse response = orderService.createOrder(multiItemRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("350.00")); // 200 + 150

        verify(productRepository, times(2)).save(any(Product.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void testGetOrders_WithPagination() {
        // Arrange
        List<Order> orders = Arrays.asList(createMockOrder(), createMockOrder());
        Page<Order> orderPage = new PageImpl<>(orders, PageRequest.of(0, 20), 2);
        
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(orderPage);
        when(productRepository.findById(anyLong())).thenReturn(Optional.of(testProduct));

        // Act
        PagedOrderResponse response = orderService.getOrders(PageRequest.of(0, 20));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getOrders()).hasSize(2);
        assertThat(response.getCurrentPage()).isEqualTo(0);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(2);

        verify(orderRepository).findAll(any(Pageable.class));
    }

    @Test
    void testGetHighValueOrders() {
        // Arrange
        List<Order> highValueOrders = Arrays.asList(createMockOrder());
        when(orderRepository.findHighValueOrders()).thenReturn(highValueOrders);
        when(productRepository.findById(anyLong())).thenReturn(Optional.of(testProduct));

        // Act
        List<OrderResponse> response = orderService.getHighValueOrders();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);

        verify(orderRepository).findHighValueOrders();
    }

    @Test
    void testGetHighValueOrders_WithMinAmount() {
        // Arrange
        BigDecimal minAmount = new BigDecimal("500.00");
        List<Order> highValueOrders = Arrays.asList(createMockOrder());
        when(orderRepository.findHighValueOrders(minAmount)).thenReturn(highValueOrders);
        when(productRepository.findById(anyLong())).thenReturn(Optional.of(testProduct));

        // Act
        List<OrderResponse> response = orderService.getHighValueOrders(minAmount);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);

        verify(orderRepository).findHighValueOrders(minAmount);
    }

    @Test
    void testGetOrderById_Success() {
        // Arrange
        Order mockOrder = createMockOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(mockOrder));
        when(productRepository.findById(anyLong())).thenReturn(Optional.of(testProduct));

        // Act
        OrderResponse response = orderService.getOrderById(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);

        verify(orderRepository).findById(1L);
    }

    @Test
    void testGetOrderById_NotFound() {
        // Arrange
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.getOrderById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order not found");

        verify(orderRepository).findById(999L);
    }

    @Test
    void testEventPublishing() {
        // Arrange
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testProduct));
        Order savedOrder = createMockOrder();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        orderService.createOrder(createOrderRequest);

        // Assert
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        OrderCreatedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getOrderId()).isEqualTo(1L);
        assertThat(capturedEvent.getCustomerId()).isEqualTo(1L);
    }

    @Test
    void testStockReservation() {
        // Arrange
        int initialStock = testProduct.getStockQuantity();
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testProduct));
        Order savedOrder = createMockOrder();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        orderService.createOrder(createOrderRequest);

        // Assert
        assertThat(testProduct.getStockQuantity()).isEqualTo(initialStock - 2);
        verify(productRepository).save(testProduct);
    }

    @Test
    void testCalculateTotalAmount() {
        // Arrange
        OrderItemRequest item1 = OrderItemRequest.builder()
                .productId(1L)
                .quantity(2)
                .build();

        OrderItemRequest item2 = OrderItemRequest.builder()
                .productId(1L)
                .quantity(3)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(Arrays.asList(item1, item2))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        when(productRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(testProduct))
                .thenReturn(Optional.of(testProduct));

        Order savedOrder = createMockOrderWithTotal(new BigDecimal("500.00"));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("500.00"));
    }

    // Helper methods
    private Order createMockOrder() {
        OrderItem item = OrderItem.builder()
                .id(1L)
                .productId(1L)
                .quantity(2)
                .price(new BigDecimal("100.00"))
                .build();

        Payment payment = Payment.builder()
                .id(1L)
                .amount(new BigDecimal("200.00"))
                .status(PaymentStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        Order order = Order.builder()
                .id(1L)
                .customerId(1L)
                .totalAmount(new BigDecimal("200.00"))
                .status(OrderStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .payment(payment)
                .build();

        item.setOrder(order);
        order.getItems().add(item);
        payment.setOrder(order);

        return order;
    }

    private Order createMockOrderWithMultipleItems() {
        OrderItem item1 = OrderItem.builder()
                .id(1L)
                .productId(1L)
                .quantity(2)
                .price(new BigDecimal("100.00"))
                .build();

        OrderItem item2 = OrderItem.builder()
                .id(2L)
                .productId(2L)
                .quantity(3)
                .price(new BigDecimal("50.00"))
                .build();

        Payment payment = Payment.builder()
                .id(1L)
                .amount(new BigDecimal("350.00"))
                .status(PaymentStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        Order order = Order.builder()
                .id(1L)
                .customerId(1L)
                .totalAmount(new BigDecimal("350.00"))
                .status(OrderStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .payment(payment)
                .build();

        item1.setOrder(order);
        item2.setOrder(order);
        order.getItems().add(item1);
        order.getItems().add(item2);
        payment.setOrder(order);

        return order;
    }

    private Order createMockOrderWithTotal(BigDecimal total) {
        Order order = createMockOrder();
        order.setTotalAmount(total);
        order.getPayment().setAmount(total);
        return order;
    }
}