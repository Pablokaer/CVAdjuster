package com.resumetailor.payment.repository;

import com.resumetailor.payment.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByStripeSessionId(String stripeSessionId);

    /**
     * Row-locks the order (SELECT ... FOR UPDATE) so the webhook and the
     * success-page reconciliation cannot both see PENDING and credit twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.stripeSessionId = :sessionId")
    Optional<Order> findByStripeSessionIdForUpdate(@Param("sessionId") String sessionId);
}
