package com.dev.orderservice.domain.repository;

import com.dev.orderservice.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findAll(Pageable pageable);
    @Query(value = "SELECT * FROM orders WHERE total_amount > :amount", nativeQuery = true)
    List<Order> findHighValueOrders(BigDecimal amount);
    @Query(value = "SELECT * FROM orders WHERE total_amount > 1000", nativeQuery = true)
    List<Order> findHighValueOrders();
}
