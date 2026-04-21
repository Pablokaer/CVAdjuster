package com.resumetailor.payment.dto;

import com.resumetailor.payment.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResponseDTO {

    private Long orderId;
    private String stripeSessionId;
    private String checkoutUrl;
    private OrderStatus status;
}
