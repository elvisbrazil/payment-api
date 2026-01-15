package dev.elvis.payments.provider;

import dev.elvis.payments.payment.AuthorizationRequest;
import dev.elvis.payments.payment.AuthorizationResult;
import dev.elvis.payments.payment.AuthorizationResult.Approved;
import dev.elvis.payments.payment.AuthorizationResult.Declined;
import dev.elvis.payments.payment.PaymentProvider;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FakePaymentProvider implements PaymentProvider {

    static final BigDecimal AMOUNT_LIMIT = new BigDecimal("10000.00");

    private static final Map<String, String> DECLINED_CARDS = Map.of(
            "4000000000000002", "card_declined",
            "4000000000009995", "insufficient_funds",
            "4000000000000069", "expired_card");

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request) {
        String cardDecline = DECLINED_CARDS.get(request.cardNumber());
        if (cardDecline != null) {
            return new Declined(cardDecline);
        }
        if (request.amount().amount().compareTo(AMOUNT_LIMIT) > 0) {
            return new Declined("amount_limit_exceeded");
        }
        return new Approved(newAuthorizationCode());
    }

    private static String newAuthorizationCode() {
        return "AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
