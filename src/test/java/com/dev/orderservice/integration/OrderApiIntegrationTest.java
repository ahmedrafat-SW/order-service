package com.dev.orderservice.integration;

import com.dev.orderservice.application.dto.CreateOrderRequest;
import com.dev.orderservice.application.dto.OrderItemRequest;
import com.dev.orderservice.domain.model.Product;
import com.dev.orderservice.domain.repository.OrderRepository;
import com.dev.orderservice.domain.repository.PaymentRepository;
import com.dev.orderservice.domain.repository.ProductRepository;
import com.dev.orderservice.domain.valueobject.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {

        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        product1 = Product.builder()
                .name("Test Product 1")
                .price(new BigDecimal("100.00"))
                .stockQuantity(400)
                .build();
        product1 = productRepository.save(product1);

        product2 = Product.builder()
                .name("Test Product 2")
                .price(new BigDecimal("200.00"))
                .stockQuantity(200)
                .build();
        product2 = productRepository.save(product2);
        productRepository.flush();
    }

    @Test
    void testCreateOrderEndpoint_Success() throws Exception {
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

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.totalAmount").value(500.00))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.payment").exists())
                .andExpect(jsonPath("$.payment.status").value("COMPLETED"));
    }

    @Test
    void testCreateOrderEndpoint_InvalidRequest_MissingCustomerId() throws Exception {
        // Arrange
        OrderItemRequest item = OrderItemRequest.builder()
                .productId(product1.getId())
                .quantity(5)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(null) // Missing
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void testCreateOrderEndpoint_InvalidRequest_EmptyItems() throws Exception {
        // Arrange
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of()) // Empty
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void testCreateOrderEndpoint_InsufficientStock() throws Exception {
        // Arrange
        OrderItemRequest item = OrderItemRequest.builder()
                .productId(product1.getId())
                .quantity(500) // More than available
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient Stock"))
                .andExpect(jsonPath("$.message").value(containsString("Insufficient stock")));
    }

    @Test
    void testCreateOrderEndpoint_ProductNotFound() throws Exception {
        // Arrange
        OrderItemRequest item = OrderItemRequest.builder()
                .productId(999L) // Non-existent product
                .quantity(5)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Product Not Found"));
    }

    @Test
    void testCreateOrderEndpoint_MultipleItems() throws Exception {
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

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(2000.00))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void testGetOrdersWithPagination_DefaultParams() throws Exception {
        // Arrange - Create some orders
        createTestOrder(product1, 1);
        createTestOrder(product1, 2);
        createTestOrder(product1, 3);

        // Act & Assert
        mockMvc.perform(get("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders", hasSize(3)))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void testGetOrdersWithPagination_CustomParams() throws Exception {
        // Arrange - Create 25 orders
        for (int i = 0; i < 25; i++) {
            createTestOrder(product1, i + 1);
        }

        // Act & Assert - First page
        mockMvc.perform(get("/api/orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESC")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(10)))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalElements").value(25));

        // Second page
        mockMvc.perform(get("/api/orders")
                        .param("page", "1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(10)))
                .andExpect(jsonPath("$.currentPage").value(1));

        // Third page
        mockMvc.perform(get("/api/orders")
                        .param("page", "2")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(5)))
                .andExpect(jsonPath("$.currentPage").value(2));
    }

    @Test
    void testGetOrderById_Success() throws Exception {
        // Arrange
        Long orderId = createTestOrder(product1, 1);

        // Act & Assert
        mockMvc.perform(get("/api/orders/" + orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.customerId").exists())
                .andExpect(jsonPath("$.totalAmount").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void testGetOrderById_NotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/orders/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order Not Found"));
    }

    @Test
    void testGetHighValueOrders_Default() throws Exception {
        // Arrange - Create orders with different amounts
        createTestOrderWithQuantity(product1, 1, 5);   // 500
        createTestOrderWithQuantity(product1, 1, 15);  // 1500 - High value
        createTestOrderWithQuantity(product2, 2, 6);   // 1200 - High value
        createTestOrderWithQuantity(product1, 1, 3);   // 300

        // Act & Assert
        mockMvc.perform(get("/api/orders/high-value")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].totalAmount", everyItem(greaterThan(1000.0))));
    }

    @Test
    void testGetHighValueOrders_CustomMinAmount() throws Exception {
        // Arrange
        createTestOrderWithQuantity(product1, 1, 5);   // 500
        createTestOrderWithQuantity(product1, 1, 15);  // 1500
        createTestOrderWithQuantity(product2, 2, 10);  // 2000

        // Act & Assert
        mockMvc.perform(get("/api/orders/high-value")
                        .param("minAmount", "1500")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void testGetHighValueOrders_NoResults() throws Exception {
        // Arrange - Create only low value orders
        createTestOrderWithQuantity(product1, 1, 5);  // 500
        createTestOrderWithQuantity(product1, 1, 3);  // 300

        // Act & Assert
        mockMvc.perform(get("/api/orders/high-value")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testOrderCreation_VerifySorting() throws Exception {
        // Arrange - Create orders with delays to ensure different timestamps
        createTestOrder(product1, 1);
        Thread.sleep(100);
        createTestOrder(product1, 2);
        Thread.sleep(100);
        createTestOrder(product1, 3);

        // Act & Assert - Descending order (newest first)
        mockMvc.perform(get("/api/orders")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESC")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].customerId").value(3))
                .andExpect(jsonPath("$.orders[1].customerId").value(2))
                .andExpect(jsonPath("$.orders[2].customerId").value(1));

        // Ascending order (oldest first)
        mockMvc.perform(get("/api/orders")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "ASC")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].customerId").value(1))
                .andExpect(jsonPath("$.orders[1].customerId").value(2))
                .andExpect(jsonPath("$.orders[2].customerId").value(3));
    }

    // Helper methods
    private Long createTestOrder(Product product, int customerId) throws Exception {
        OrderItemRequest item = OrderItemRequest.builder()
                .productId(product.getId())
                .quantity(5)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId((long) customerId)
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createTestOrderWithQuantity(Product product, int customerId, int quantity) throws Exception {
        OrderItemRequest item = OrderItemRequest.builder()
                .productId(product.getId())
                .quantity(quantity)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId((long) customerId)
                .items(List.of(item))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}