package dev.elvis.payments.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(UUID id, UUID paymentId, BigDecimal amount, String currency, Instant createdAt) {

    static RefundResponse from(Refund refund) {
        var amount = refund.amount();
        return new RefundResponse(
                refund.id(),
                refund.payment().id(),
                amount.amount(),
                amount.currency().getCurrencyCode(),
                refund.createdAt());
    }
}
