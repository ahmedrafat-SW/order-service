package com.dev.orderservice.application.service;

import com.dev.orderservice.application.dto.*;
import com.dev.orderservice.domain.event.OrderCreatedEvent;
import com.dev.orderservice.domain.exception.InsufficientStockException;
import com.dev.orderservice.domain.exception.OrderNotFoundException;
import com.dev.orderservice.domain.exception.PaymentFailedException;
import com.dev.orderservice.domain.exception.ProductNotFoundException;
import com.dev.orderservice.domain.model.*;
import com.dev.orderservice.domain.repository.OrderRepository;
import com.dev.orderservice.domain.repository.PaymentRepository;
import com.dev.orderservice.domain.repository.ProductRepository;
import com.dev.orderservice.domain.valueobject.OrderStatus;
import com.dev.orderservice.domain.valueobject.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        for (OrderItemRequest itemRequest : request.getItems()) {
            processOrderItem(order, itemRequest);
        }

        order.calculateTotalAmount();
        Payment payment = createPayment(order, request.getPaymentMethod());
        order.setPayment(payment);
        processPayment(payment);
        order.confirm();
        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with ID: {}", savedOrder.getId());
        eventPublisher.publishEvent(new OrderCreatedEvent(savedOrder));
        return mapToOrderResponse(savedOrder);
    }

    private void processOrderItem(Order order, OrderItemRequest itemRequest) {
        Product product = productRepository.findByIdForUpdate(itemRequest.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));
        if (!product.hasStock(itemRequest.getQuantity())) {
            throw new InsufficientStockException(
                    product.getId(),
                    itemRequest.getQuantity(),
                    product.getStockQuantity()
            );
        }
        product.reserveStock(itemRequest.getQuantity());
        productRepository.save(product);
        OrderItem orderItem = OrderItem.builder()
                .productId(product.getId())
                .quantity(itemRequest.getQuantity())
                .price(product.getPrice())
                .build();
        order.addItem(orderItem);
        log.debug("Added item to order - Product ID: {}, Quantity: {}", 
                product.getId(), itemRequest.getQuantity());
    }

    private Payment createPayment(Order order, com.dev.orderservice.domain.valueobject.PaymentMethod paymentMethod) {
        return Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .paymentMethod(paymentMethod)
                .build();
    }

    private void processPayment(Payment payment) {
        log.info("Processing payment of {} using {}", 
                payment.getAmount(), payment.getPaymentMethod());
        boolean paymentSuccess = simulatePaymentGateway(payment);
        if (paymentSuccess) {
            payment.complete();
            log.info("Payment completed successfully");
        } else {
            payment.fail();
            throw new PaymentFailedException("Payment gateway declined the transaction");
        }
    }

    private boolean simulatePaymentGateway(Payment payment) {
        return payment.getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    @Transactional(readOnly = true)
    public PagedOrderResponse getOrders(Pageable pageable) {
        log.info("Fetching orders - Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<Order> orderPage = orderRepository.findAll(pageable);
        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
        return PagedOrderResponse.builder()
                .orders(orderResponses)
                .currentPage(orderPage.getNumber())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .size(orderPage.getSize())
                .build();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        log.info("Fetching order by ID: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getHighValueOrders() {
        log.info("Fetching high-value orders (total > 1000)");
        List<Order> orders = orderRepository.findHighValueOrders();
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getHighValueOrders(BigDecimal minAmount) {
        log.info("Fetching high-value orders (total > {})", minAmount);
        List<Order> orders = orderRepository.findHighValueOrders(minAmount);
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::mapToOrderItemResponse)
                .collect(Collectors.toList());
        PaymentResponse paymentResponse = order.getPayment() != null 
                ? mapToPaymentResponse(order.getPayment()) 
                : null;
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .payment(paymentResponse)
                .build();
    }

    private OrderItemResponse mapToOrderItemResponse(OrderItem item) {
        String productName = productRepository.findById(item.getProductId())
                .map(Product::getName)
                .orElse("Unknown Product");
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(productName)
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .build();
    }

}
