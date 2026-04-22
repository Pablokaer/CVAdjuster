package com.resumetailor.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Audit record of every Stripe webhook event that was successfully processed.
 *
 * The UNIQUE constraint on stripeEventId is the race serializer that makes
 * concurrent duplicate deliveries safe: the second INSERT fails at the DB
 * level, rolling back the entire transaction including any credit grant,
 * so credits are never applied more than once per event.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "stripe_processed_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_stripe_event_id",
        columnNames = "stripe_event_id"
    )
)
public class StripeProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_event_id", nullable = false, unique = true)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
