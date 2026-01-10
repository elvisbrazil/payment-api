package dev.elvis.payments.payment;

public class InvalidPaymentTransitionException extends RuntimeException {

    private final PaymentStatus from;
    private final PaymentStatus to;

    public InvalidPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Cannot move payment from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public PaymentStatus from() {
        return from;
    }

    public PaymentStatus to() {
        return to;
    }
}
