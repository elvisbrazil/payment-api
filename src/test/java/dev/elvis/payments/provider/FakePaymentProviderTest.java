package dev.elvis.payments.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.elvis.payments.payment.AuthorizationRequest;
import dev.elvis.payments.payment.AuthorizationResult.Approved;
import dev.elvis.payments.payment.AuthorizationResult.Declined;
import dev.elvis.payments.payment.Money;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class FakePaymentProviderTest {

    private final FakePaymentProvider provider = new FakePaymentProvider();

    @Test
    void approvesRegularCard() {
        var result = provider.authorize(request("4242424242424242", "150.00"));

        assertThat(result).isInstanceOfSatisfying(Approved.class, approved -> assertThat(approved.authorizationCode())
                .startsWith("AUTH-"));
    }

    @ParameterizedTest
    @CsvSource({
        "4000000000000002, card_declined",
        "4000000000009995, insufficient_funds",
        "4000000000000069, expired_card"
    })
    void declinesTestCards(String cardNumber, String reason) {
        var result = provider.authorize(request(cardNumber, "10.00"));

        assertThat(result).isEqualTo(new Declined(reason));
    }

    @Test
    void declinesAmountsAboveLimit() {
        assertThat(provider.authorize(request("4242424242424242", "10000.01")))
                .isEqualTo(new Declined("amount_limit_exceeded"));
        assertThat(provider.authorize(request("4242424242424242", "10000.00"))).isInstanceOf(Approved.class);
    }

    private static AuthorizationRequest request(String cardNumber, String amount) {
        return new AuthorizationRequest(UUID.randomUUID(), Money.of(amount, "BRL"), cardNumber);
    }
}
