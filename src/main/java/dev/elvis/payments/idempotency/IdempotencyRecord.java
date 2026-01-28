package dev.elvis.payments.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key")
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    IdempotencyRecord(String key, String requestHash, String responseBody) {
        this.key = key;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    boolean matches(String hash) {
        return requestHash.equals(hash);
    }

    String responseBody() {
        return responseBody;
    }
}
