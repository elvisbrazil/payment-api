package dev.elvis.payments.payment;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new InvalidMoneyException("Amount must not be negative");
        }
        if (amount.stripTrailingZeros().scale() > currency.getDefaultFractionDigits()) {
            throw new InvalidMoneyException(
                    "Amount has more decimal places than " + currency.getCurrencyCode() + " allows");
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyOf(currencyCode));
    }

    public static Money of(String amount, String currencyCode) {
        return of(new BigDecimal(amount), currencyCode);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new InvalidMoneyException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " and " + other.currency.getCurrencyCode());
        }
    }

    private static Currency currencyOf(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidMoneyException("Unknown currency: " + code);
        }
    }
}
