package com.resumetailor.payment.repository;

import com.resumetailor.payment.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByStripeSessionId(String stripeSessionId);
}
