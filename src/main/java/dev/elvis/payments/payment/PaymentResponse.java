package dev.elvis.payments.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        BigDecimal refundedAmount,
        String cardLast4,
        String authorizationCode,
        String declineReason,
        List<RefundResponse> refunds,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.status(),
                payment.amount().amount(),
                payment.currency().getCurrencyCode(),
                payment.refundedAmount().amount(),
                payment.cardLast4(),
                payment.authorizationCode(),
                payment.declineReason(),
                payment.refunds().stream().map(RefundResponse::from).toList(),
                payment.createdAt(),
                payment.updatedAt());
    }
}
