package dev.elvis.payments.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void startsPendingWithNothingRefunded() {
        var payment = Payment.pending(Money.of("100.00", "BRL"), "4242");

        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.refundedAmount().isZero()).isTrue();
        assertThat(payment.refunds()).isEmpty();
    }

    @Test
    void authorizesAndCaptures() {
        var payment = Payment.pending(Money.of("100.00", "BRL"), "4242");

        payment.authorize("AUTH-1");
        payment.capture();

        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.authorizationCode()).isEqualTo("AUTH-1");
    }

    @Test
    void keepsDeclineReason() {
        var payment = Payment.pending(Money.of("100.00", "BRL"), "0002");

        payment.decline("card_declined");

        assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.declineReason()).isEqualTo("card_declined");
    }

    @Test
    void voidsAnAuthorizedPayment() {
        var payment = authorized("50.00");

        payment.voidAuthorization();

        assertThat(payment.status()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    void cannotCaptureDeclinedPayment() {
        var payment = Payment.pending(Money.of("100.00", "BRL"), "0002");
        payment.decline("card_declined");

        assertThatThrownBy(payment::capture)
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessage("Cannot move payment from DECLINED to CAPTURED");
    }

    @Test
    void cannotVoidCapturedPayment() {
        var payment = captured("50.00");

        assertThatThrownBy(payment::voidAuthorization).isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void cannotRefundBeforeCapture() {
        var payment = authorized("50.00");

        assertThatThrownBy(() -> payment.refund(Money.of("10.00", "BRL")))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.refunds()).isEmpty();
    }

    @Test
    void partialRefundKeepsPaymentRefundable() {
        var payment = captured("100.00");

        var refund = payment.refund(Money.of("30.00", "BRL"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.refundedAmount()).isEqualTo(Money.of("30.00", "BRL"));
        assertThat(refund.amount()).isEqualTo(Money.of("30.00", "BRL"));
        assertThat(payment.refunds()).containsExactly(refund);
    }

    @Test
    void refundsUpToCapturedAmountInSeveralSteps() {
        var payment = captured("100.00");

        payment.refund(Money.of("30.00", "BRL"));
        payment.refund(Money.of("70.00", "BRL"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.refunds()).hasSize(2);
    }

    @Test
    void rejectsRefundAboveCapturedAmount() {
        var payment = captured("100.00");

        assertThatThrownBy(() -> payment.refund(Money.of("100.01", "BRL")))
                .isInstanceOf(RefundExceedsCapturedAmountException.class);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void rejectsRefundAboveRemainingAmount() {
        var payment = captured("100.00");
        payment.refund(Money.of("60.00", "BRL"));

        assertThatThrownBy(() -> payment.refund(Money.of("50.00", "BRL")))
                .isInstanceOfSatisfying(
                        RefundExceedsCapturedAmountException.class,
                        e -> assertThat(e.refundable()).isEqualTo(Money.of("40.00", "BRL")));
        assertThat(payment.refundedAmount()).isEqualTo(Money.of("60.00", "BRL"));
    }

    @Test
    void cannotRefundFullyRefundedPayment() {
        var payment = captured("100.00");
        payment.refund(Money.of("100.00", "BRL"));

        assertThatThrownBy(() -> payment.refund(Money.of("1.00", "BRL")))
                .isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void rejectsZeroRefund() {
        var payment = captured("100.00");

        assertThatThrownBy(() -> payment.refund(Money.of("0.00", "BRL"))).isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void rejectsRefundInAnotherCurrency() {
        var payment = captured("100.00");

        assertThatThrownBy(() -> payment.refund(Money.of("10.00", "USD"))).isInstanceOf(InvalidMoneyException.class);
    }

    private static Payment authorized(String amount) {
        var payment = Payment.pending(Money.of(amount, "BRL"), "4242");
        payment.authorize("AUTH-1");
        return payment;
    }

    private static Payment captured(String amount) {
        var payment = authorized(amount);
        payment.capture();
        return payment;
    }
}
