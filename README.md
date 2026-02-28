# payment-api

A small Spring Boot service that models card-style payments: authorization through a payment provider, capture, void
and partial refunds. Payment status follows an explicit state machine, and the two operations that move money
(`POST /payments` and `POST /payments/{id}/refunds`) require an `Idempotency-Key` header so clients can retry safely.

## Running

Requires Java 21. Docker is only needed to run the app against PostgreSQL.

```sh
./mvnw verify                 # tests, formatting check (Spotless) and Checkstyle
docker compose up -d          # PostgreSQL on localhost:5432
./mvnw spring-boot:run        # API on localhost:8080
```

Tests run against H2 in PostgreSQL mode, with the same Flyway migrations.

## Payment lifecycle

```
PENDING -> AUTHORIZED -> CAPTURED -> PARTIALLY_REFUNDED -> REFUNDED
   |           |             \______________________________/
   v           v
DECLINED     VOIDED
```

Any other transition returns `409 Conflict`. Errors are returned as `application/problem+json`.

| Method | Path                     | Notes                                 |
|--------|--------------------------|---------------------------------------|
| POST   | `/payments`              | Requires `Idempotency-Key`            |
| GET    | `/payments/{id}`         |                                       |
| POST   | `/payments/{id}/capture` | Captures the full authorized amount   |
| POST   | `/payments/{id}/void`    | Only for authorized payments          |
| POST   | `/payments/{id}/refunds` | Requires `Idempotency-Key`            |

## Design notes

- `Payment` owns its state transitions and refund rules; the service only loads, calls the provider and saves.
- Amounts are `BigDecimal` plus an ISO 4217 currency. `Money` rejects more decimal places than the currency allows and
  normalizes the scale, so `100` and `100.00` are the same value.
- Idempotency keys are stored with a SHA-256 hash of the operation and request body, plus the serialized response.
  Same key and same request replays the stored response (`Idempotent-Replayed: true`); same key with a different
  request is a `422`. Only successful responses are stored, so a request that failed validation can be retried with
  the same key.
- The key is written in the same transaction as the payment. If two requests with the same key race, the loser gets a
  `409` and its transaction is rolled back. The provider call is not undone in that case; a real integration would
  pass the key down to the provider.
- Only authorization goes through `PaymentProvider`. Capture, void and refund are recorded locally to keep the scope
  small.
- Card numbers are validated (length and Luhn) and only the last four digits are stored.
