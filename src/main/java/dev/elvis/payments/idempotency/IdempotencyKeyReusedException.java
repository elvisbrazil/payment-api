package dev.elvis.payments.idempotency;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency key " + key + " was already used with a different request");
    }
}
