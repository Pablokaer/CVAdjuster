package com.resumetailor.payment.repository;

import com.resumetailor.payment.entity.StripeProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripeProcessedEventRepository extends JpaRepository<StripeProcessedEvent, Long> {

    boolean existsByStripeEventId(String stripeEventId);
}
