package dev.elvis.payments.payment;

import dev.elvis.payments.payment.AuthorizationResult.Approved;
import dev.elvis.payments.payment.AuthorizationResult.Declined;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentProvider provider;

    public PaymentService(PaymentRepository payments, PaymentProvider provider) {
        this.payments = payments;
        this.provider = provider;
    }

    public Payment create(Money amount, String cardNumber) {
        var payment = Payment.pending(amount, lastFour(cardNumber));
        var result = provider.authorize(new AuthorizationRequest(payment.id(), amount, cardNumber));
        switch (result) {
            case Approved approved -> payment.authorize(approved.authorizationCode());
            case Declined declined -> payment.decline(declined.reason());
        }
        return payments.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findWithRefundsById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public Payment capture(UUID id) {
        var payment = get(id);
        payment.capture();
        return payment;
    }

    public Payment voidAuthorization(UUID id) {
        var payment = get(id);
        payment.voidAuthorization();
        return payment;
    }

    public Refund refund(UUID id, BigDecimal amount) {
        var payment = get(id);
        return payment.refund(new Money(amount, payment.currency()));
    }

    private static String lastFour(String cardNumber) {
        return cardNumber.substring(cardNumber.length() - 4);
    }
}
