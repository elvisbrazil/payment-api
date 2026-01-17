package dev.elvis.payments.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import dev.elvis.payments.payment.AuthorizationResult.Approved;
import dev.elvis.payments.payment.AuthorizationResult.Declined;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository payments;

    @Mock
    private PaymentProvider provider;

    private PaymentService service() {
        return new PaymentService(payments, provider);
    }

    @Test
    void authorizesPaymentWhenProviderApproves() {
        given(provider.authorize(any())).willReturn(new Approved("AUTH-42"));
        given(payments.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        var payment = service().create(Money.of("99.90", "BRL"), "4242424242424242");

        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.authorizationCode()).isEqualTo("AUTH-42");
        assertThat(payment.cardLast4()).isEqualTo("4242");
    }

    @Test
    void declinesPaymentWhenProviderDeclines() {
        given(provider.authorize(any())).willReturn(new Declined("insufficient_funds"));
        given(payments.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        var payment = service().create(Money.of("99.90", "BRL"), "4000000000009995");

        assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.declineReason()).isEqualTo("insufficient_funds");
    }

    @Test
    void sendsFullCardNumberAndPaymentIdToProvider() {
        given(provider.authorize(any())).willReturn(new Approved("AUTH-42"));
        given(payments.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        var payment = service().create(Money.of("10.00", "USD"), "5555555555554444");

        var request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(provider).authorize(request.capture());
        assertThat(request.getValue().cardNumber()).isEqualTo("5555555555554444");
        assertThat(request.getValue().paymentId()).isEqualTo(payment.id());
        assertThat(request.getValue().amount()).isEqualTo(Money.of("10.00", "USD"));
    }

    @Test
    void failsForUnknownPayment() {
        var id = UUID.randomUUID();
        given(payments.findWithRefundsById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().capture(id))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void capturesAuthorizedPayment() {
        var payment = authorizedPayment();
        given(payments.findWithRefundsById(payment.id())).willReturn(Optional.of(payment));

        service().capture(payment.id());

        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void refundsCapturedPayment() {
        var payment = authorizedPayment();
        payment.capture();
        given(payments.findWithRefundsById(payment.id())).willReturn(Optional.of(payment));

        var refund = service().refund(payment.id(), Money.of("20.00", "BRL"));

        assertThat(refund.payment()).isSameAs(payment);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    private static Payment authorizedPayment() {
        var payment = Payment.pending(Money.of("100.00", "BRL"), "4242");
        payment.authorize("AUTH-1");
        return payment;
    }
}
