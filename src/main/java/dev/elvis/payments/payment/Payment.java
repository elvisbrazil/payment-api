package dev.elvis.payments.payment;

import static dev.elvis.payments.payment.PaymentStatus.AUTHORIZED;
import static dev.elvis.payments.payment.PaymentStatus.CAPTURED;
import static dev.elvis.payments.payment.PaymentStatus.DECLINED;
import static dev.elvis.payments.payment.PaymentStatus.PARTIALLY_REFUNDED;
import static dev.elvis.payments.payment.PaymentStatus.PENDING;
import static dev.elvis.payments.payment.PaymentStatus.REFUNDED;
import static dev.elvis.payments.payment.PaymentStatus.VOIDED;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "card_last4", nullable = false, length = 4)
    private String cardLast4;

    @Column(name = "authorization_code", length = 64)
    private String authorizationCode;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt")
    private List<Refund> refunds = new ArrayList<>();

    protected Payment() {}

    private Payment(Money amount, String cardLast4) {
        this.id = UUID.randomUUID();
        this.amount = amount.amount();
        this.currency = amount.currency().getCurrencyCode();
        this.refundedAmount = BigDecimal.ZERO;
        this.status = PENDING;
        this.cardLast4 = cardLast4;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Payment pending(Money amount, String cardLast4) {
        return new Payment(amount, cardLast4);
    }

    public void authorize(String authorizationCode) {
        moveTo(AUTHORIZED);
        this.authorizationCode = authorizationCode;
    }

    public void decline(String reason) {
        moveTo(DECLINED);
        this.declineReason = reason;
    }

    public void capture() {
        moveTo(CAPTURED);
    }

    public void voidAuthorization() {
        moveTo(VOIDED);
    }

    public Refund refund(Money refundAmount) {
        if (refundAmount.isZero()) {
            throw new InvalidMoneyException("Refund amount must be greater than zero");
        }
        Money totalRefunded = refundedAmount().plus(refundAmount);
        PaymentStatus target = totalRefunded.equals(amount()) ? REFUNDED : PARTIALLY_REFUNDED;
        requireTransition(target);
        if (totalRefunded.isGreaterThan(amount())) {
            throw new RefundExceedsCapturedAmountException(amount().minus(refundedAmount()));
        }

        Refund refund = new Refund(this, refundAmount);
        refunds.add(refund);
        refundedAmount = totalRefunded.amount();
        moveTo(target);
        return refund;
    }

    private void moveTo(PaymentStatus target) {
        requireTransition(target);
        status = target;
        updatedAt = Instant.now();
    }

    private void requireTransition(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidPaymentTransitionException(status, target);
        }
    }

    public UUID id() {
        return id;
    }

    public Money amount() {
        return new Money(amount, currency());
    }

    public Money refundedAmount() {
        return new Money(refundedAmount, currency());
    }

    public Currency currency() {
        return Currency.getInstance(currency);
    }

    public PaymentStatus status() {
        return status;
    }

    public String cardLast4() {
        return cardLast4;
    }

    public String authorizationCode() {
        return authorizationCode;
    }

    public String declineReason() {
        return declineReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<Refund> refunds() {
        return Collections.unmodifiableList(refunds);
    }
}
