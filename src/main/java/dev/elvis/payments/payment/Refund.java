package dev.elvis.payments.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Refund() {}

    Refund(Payment payment, Money amount) {
        this.id = UUID.randomUUID();
        this.payment = payment;
        this.amount = amount.amount();
        this.createdAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public Payment payment() {
        return payment;
    }

    public Money amount() {
        return new Money(amount, payment.currency());
    }

    public Instant createdAt() {
        return createdAt;
    }
}
