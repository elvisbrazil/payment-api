package dev.elvis.payments.payment;

import java.util.UUID;

public record AuthorizationRequest(UUID paymentId, Money amount, String cardNumber) {}
