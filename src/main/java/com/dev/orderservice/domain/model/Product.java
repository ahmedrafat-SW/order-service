package com.dev.orderservice.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
        @Version
        private Long version;
    // Domain methods
    public boolean hasStock(Integer requestedQuantity) {
        return stockQuantity >= requestedQuantity;
    }
    public void reserveStock(Integer quantity) {
        if (!hasStock(quantity)) {
            throw new IllegalStateException(
                String.format("Insufficient stock for product %s. Available: %d, Requested: %d", 
                    name, stockQuantity, quantity)
            );
        }
        this.stockQuantity -= quantity;
    }
    public void releaseStock(Integer quantity) {
        this.stockQuantity += quantity;
    }

}
