package dev.elvis.payments.payment;

public class RefundExceedsCapturedAmountException extends RuntimeException {

    private final Money refundable;

    public RefundExceedsCapturedAmountException(Money refundable) {
        super("Refund exceeds the refundable amount of " + refundable.amount() + " "
                + refundable.currency().getCurrencyCode());
        this.refundable = refundable;
    }

    public Money refundable() {
        return refundable;
    }
}
