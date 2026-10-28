package com.resumetailor.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.payment.exception.PaymentException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;

import java.util.HashMap;
import java.util.Map;

/**
 * The parts of a Stripe Checkout Session that fulfilment depends on, taken either
 * from a webhook event or from the Stripe API, so both paths are checked the same way.
 *
 * @param amountTotal total charged, in the smallest currency unit (null if absent)
 */
public record SessionFacts(
        String sessionId,
        String paymentStatus,
        Long amountTotal,
        String currency,
        Map<String, String> metadata) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SessionFacts {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean isPaid() {
        return "paid".equals(paymentStatus);
    }

    /**
     * Reads the session from the raw webhook JSON.
     *
     * Using getRawJson() instead of getDataObjectDeserializer().getObject() avoids
     * a silent Optional.empty() when the Stripe API version in the event payload does
     * not match the version the SDK was compiled against.
     */
    public static SessionFacts fromEvent(Event event) {
        return fromJson(event.getDataObjectDeserializer().getRawJson());
    }

    /** Parses a Checkout Session object as JSON (the {@code data.object} of a webhook event). */
    public static SessionFacts fromJson(String json) {
        try {
            JsonNode data = MAPPER.readTree(json);
            JsonNode id = data.get("id");
            if (id == null || id.isNull()) {
                throw new PaymentException("Stripe session JSON has no id");
            }

            Map<String, String> metadata = new HashMap<>();
            data.path("metadata").fields().forEachRemaining(e -> {
                if (!e.getValue().isNull()) metadata.put(e.getKey(), e.getValue().asText());
            });

            JsonNode amount = data.get("amount_total");
            return new SessionFacts(
                    id.asText(),
                    textOrNull(data.get("payment_status")),
                    amount == null || amount.isNull() ? null : amount.asLong(),
                    textOrNull(data.get("currency")),
                    metadata);
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentException("Could not parse Stripe session: " + e.getMessage(), e);
        }
    }

    public static SessionFacts fromSession(Session session) {
        return new SessionFacts(
                session.getId(),
                session.getPaymentStatus(),
                session.getAmountTotal(),
                session.getCurrency(),
                session.getMetadata());
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
