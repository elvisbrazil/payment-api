package dev.elvis.payments.payment;

public interface PaymentProvider {

    AuthorizationResult authorize(AuthorizationRequest request);
}
