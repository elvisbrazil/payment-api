package dev.elvis.payments.payment;

import static dev.elvis.payments.payment.PaymentStatus.AUTHORIZED;
import static dev.elvis.payments.payment.PaymentStatus.CAPTURED;
import static dev.elvis.payments.payment.PaymentStatus.PARTIALLY_REFUNDED;
import static dev.elvis.payments.payment.PaymentStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentStatusTest {

    @ParameterizedTest
    @CsvSource({
        "PENDING, AUTHORIZED",
        "PENDING, DECLINED",
        "AUTHORIZED, CAPTURED",
        "AUTHORIZED, VOIDED",
        "CAPTURED, PARTIALLY_REFUNDED",
        "CAPTURED, REFUNDED",
        "PARTIALLY_REFUNDED, PARTIALLY_REFUNDED",
        "PARTIALLY_REFUNDED, REFUNDED"
    })
    void allowsValidTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "PENDING, CAPTURED",
        "PENDING, REFUNDED",
        "AUTHORIZED, REFUNDED",
        "AUTHORIZED, DECLINED",
        "CAPTURED, VOIDED",
        "CAPTURED, AUTHORIZED",
        "PARTIALLY_REFUNDED, VOIDED"
    })
    void rejectsInvalidTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentStatus.class,
            names = {"DECLINED", "VOIDED", "REFUNDED"})
    void finalStatusesHaveNoWayOut(PaymentStatus status) {
        assertThat(status.isFinal()).isTrue();
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(status.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void nonFinalStatuses() {
        assertThat(PENDING.isFinal()).isFalse();
        assertThat(AUTHORIZED.isFinal()).isFalse();
        assertThat(CAPTURED.isFinal()).isFalse();
        assertThat(PARTIALLY_REFUNDED.isFinal()).isFalse();
    }
}
