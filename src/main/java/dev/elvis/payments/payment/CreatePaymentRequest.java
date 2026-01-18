package dev.elvis.payments.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String currency,
        @NotBlank @Pattern(regexp = "\\d{12,19}", message = "must contain 12 to 19 digits") String cardNumber) {

    Money money() {
        return Money.of(amount, currency);
    }
}
