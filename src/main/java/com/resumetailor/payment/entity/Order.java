package com.resumetailor.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String productName;

    /** Amount in cents (e.g. 2999 = $29.99) */
    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** Stripe Checkout Session ID (cs_test_...) */
    @Column(unique = true)
    private String stripeSessionId;

    /** FK to the user who initiated this payment */
    private Long userId;

    /** Credits to add to the user after a successful payment */
    private Integer creditsToAdd;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
