package dev.elvis.payments.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-1.00", "BRL"))
                .isInstanceOf(InvalidMoneyException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThatThrownBy(() -> Money.of("10.00", "XYZ")).isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void rejectsMoreDecimalPlacesThanCurrencyAllows() {
        assertThatThrownBy(() -> Money.of("10.5", "JPY")).isInstanceOf(InvalidMoneyException.class);
        assertThatThrownBy(() -> Money.of("10.001", "USD")).isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void acceptsTrailingZerosBeyondCurrencyPrecision() {
        assertThat(Money.of("10.000", "USD").amount()).isEqualByComparingTo("10");
    }

    @Test
    void addsAndSubtractsInSameCurrency() {
        var total = Money.of("10.50", "BRL").plus(Money.of("4.50", "BRL"));

        assertThat(total.amount()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(total.minus(Money.of("15", "BRL")).isZero()).isTrue();
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of("1.00", "BRL").plus(Money.of("1.00", "USD")))
                .isInstanceOf(InvalidMoneyException.class)
                .hasMessageContaining("mismatch");
    }

    @Test
    void comparesAmounts() {
        assertThat(Money.of("10.01", "EUR").isGreaterThan(Money.of("10.00", "EUR"))).isTrue();
        assertThat(Money.of("10.00", "EUR").isGreaterThan(Money.of("10", "EUR"))).isFalse();
    }
}
