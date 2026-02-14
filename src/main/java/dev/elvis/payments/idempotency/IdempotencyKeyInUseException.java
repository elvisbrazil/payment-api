package dev.elvis.payments.idempotency;

public class IdempotencyKeyInUseException extends RuntimeException {

    public IdempotencyKeyInUseException(String key) {
        super("Another request with idempotency key " + key + " was processed concurrently, retry to get its result");
    }
}
