package dev.elvis.payments.idempotency;

public record IdempotentResponse<T>(T body, boolean replayed) {}
