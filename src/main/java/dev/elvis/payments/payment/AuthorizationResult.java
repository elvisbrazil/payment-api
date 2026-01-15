package dev.elvis.payments.payment;

public sealed interface AuthorizationResult {

    record Approved(String authorizationCode) implements AuthorizationResult {}

    record Declined(String reason) implements AuthorizationResult {}
}
