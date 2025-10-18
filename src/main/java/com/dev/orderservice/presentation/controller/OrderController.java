package com.dev.orderservice.presentation.controller;

import com.dev.orderservice.application.dto.CreateOrderRequest;
import com.dev.orderservice.application.dto.OrderResponse;
import com.dev.orderservice.application.dto.PagedOrderResponse;
import com.dev.orderservice.application.service.OrderApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Operations related to customer orders")
public class OrderController {

    private final OrderApplicationService orderService;
    private final Logger log = LoggerFactory.getLogger(OrderController.class);

    @Operation(
            summary = "Create a new order",
            description = "Creates a new order for a given customer with provided items and total amount."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order successfully created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content)
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody @Parameter(description = "Order creation details", required = true)
            CreateOrderRequest request) {
        log.info("Received create order request for customer: {}", request.getCustomerId());
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get paginated list of orders",
            description = "Retrieves a paginated list of orders with sorting options."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of orders retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PagedOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
                    content = @Content)
    })
    @GetMapping
    public ResponseEntity<PagedOrderResponse> getOrders(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("Fetching orders - page: {}, size: {}, sortBy: {}, direction: {}", page, size, sortBy, sortDirection);
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        PagedOrderResponse response = orderService.getOrders(pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get order by ID",
            description = "Retrieves a specific order by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order ID", required = true) @PathVariable Long id) {
        log.info("Fetching order by ID: {}", id);
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get high-value orders",
            description = "Retrieves all orders with total amount greater than or equal to the specified minimum amount. If no minimum is provided, returns all high-value orders based on business logic."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of high-value orders retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid amount parameter",
                    content = @Content)
    })
    @GetMapping("/high-value")
    public ResponseEntity<List<OrderResponse>> getHighValueOrders(
            @Parameter(description = "Minimum total amount to filter high-value orders")
            @RequestParam(required = false) BigDecimal minAmount) {
        log.info("Fetching high-value orders with min amount: {}", minAmount);
        List<OrderResponse> response = minAmount != null
                ? orderService.getHighValueOrders(minAmount)
                : orderService.getHighValueOrders();
        return ResponseEntity.ok(response);
    }
}
