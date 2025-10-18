package com.dev.orderservice.domain.model;

import com.dev.orderservice.domain.valueobject.PaymentMethod;
import com.dev.orderservice.domain.valueobject.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    // Domain methods
    public void complete() {
        this.status = PaymentStatus.COMPLETED;
    }
    public void fail() {
        this.status = PaymentStatus.FAILED;
    }
    public boolean isSuccessful() {
        return status == PaymentStatus.COMPLETED;
    }
}
