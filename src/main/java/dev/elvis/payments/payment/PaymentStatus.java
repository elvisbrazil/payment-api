package dev.elvis.payments.payment;

import java.util.Set;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    DECLINED,
    CAPTURED,
    VOIDED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<PaymentStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> Set.of(AUTHORIZED, DECLINED);
            case AUTHORIZED -> Set.of(CAPTURED, VOIDED);
            case CAPTURED, PARTIALLY_REFUNDED -> Set.of(PARTIALLY_REFUNDED, REFUNDED);
            case DECLINED, VOIDED, REFUNDED -> Set.of();
        };
    }

    public boolean isFinal() {
        return allowedTargets().isEmpty();
    }
}
