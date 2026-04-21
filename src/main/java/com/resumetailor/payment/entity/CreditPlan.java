package com.resumetailor.payment.entity;

import com.resumetailor.payment.exception.PaymentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════
 *  ÚNICO LUGAR PARA CONFIGURAR PREÇOS E CRÉDITOS
 * ════════════════════════════════════════════════════════════════
 *
 *  1. CREDITS_PER_RESUME — quantos créditos uma geração consome.
 *     Altere aqui; o desconto no serviço e os cálculos do
 *     frontend se atualizam automaticamente.
 *
 *  2. Cada plano:
 *     NOME ("Label", creditsToAdd, amountInCents, currency, featured)
 *
 *  Exemplos de amount: 199 = $1.99 | 999 = $9.99 | 2999 = $29.99
 * ════════════════════════════════════════════════════════════════
 */
@Getter
@RequiredArgsConstructor
public enum CreditPlan {

    // ── Planos disponíveis ────────────────────────────────────────────────
    //         label       créditos  centavos  moeda   destaque
    STARTER ("Starter",      5,       199L,   "usd",   false),
    PRO     ("Pro",         20,      1499L,   "usd",   true ),
    PREMIUM ("Premium",     50,      2999L,   "usd",   false);
    // ─────────────────────────────────────────────────────────────────────

    // ── Custo de cada geração de currículo ────────────────────────────────
    public static final int CREDITS_PER_RESUME = 1;
    // ─────────────────────────────────────────────────────────────────────

    private final String  displayName;
    private final int     creditsToAdd;
    private final long    amount;      // in cents
    private final String  currency;
    private final boolean featured;

    // ── Helpers usados pelo Thymeleaf ─────────────────────────────────────

    /** Quantos currículos este plano permite gerar. */
    public int getResumesIncluded() {
        return creditsToAdd / CREDITS_PER_RESUME;
    }

    /** Custo em dólar por currículo gerado com este plano. Ex.: "$0.40" */
    public String getCostPerResume() {
        return String.format("$%.2f per resume", (amount / 100.0) / getResumesIncluded());
    }

    /** Preço formatado para exibição. Ex.: "$14.99" */
    public String getDisplayPrice() {
        return String.format("$%.2f", amount / 100.0);
    }

    /** Nome enviado ao Stripe na criação da sessão. */
    public String getProductName() {
        return displayName + " – " + creditsToAdd + " Credits";
    }

    public static CreditPlan fromId(String id) {
        for (CreditPlan plan : values()) {
            if (plan.name().equalsIgnoreCase(id)) return plan;
        }
        throw new PaymentException("Invalid plan ID: " + id);
    }
}
