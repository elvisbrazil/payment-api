package dev.elvis.payments.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService service;

    @Autowired
    private IdempotencyRecordRepository records;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void runsActionOnlyOnceForSameKey() {
        var key = UUID.randomUUID().toString();
        var calls = new AtomicInteger();

        var first = service.execute(key, "op", Map.of("a", 1), Result.class, () -> new Result(calls.incrementAndGet()));
        var second =
                service.execute(key, "op", Map.of("a", 1), Result.class, () -> new Result(calls.incrementAndGet()));

        assertThat(calls).hasValue(1);
        assertThat(first).isEqualTo(new IdempotentResponse<>(new Result(1), false));
        assertThat(second).isEqualTo(new IdempotentResponse<>(new Result(1), true));
    }

    @Test
    void sameBodyForAnotherOperationIsAConflict() {
        var key = UUID.randomUUID().toString();
        service.execute(key, "op-a", Map.of("a", 1), Result.class, () -> new Result(1));

        assertThatThrownBy(() -> service.execute(key, "op-b", Map.of("a", 1), Result.class, () -> new Result(2)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void rejectsOverlongKey() {
        var key = "k".repeat(256);

        assertThatThrownBy(() -> service.execute(key, "op", Map.of(), Result.class, () -> new Result(1)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void reportsConflictWhenAnotherRequestStoredTheKeyFirst() {
        var key = UUID.randomUUID().toString();
        var concurrentRequest = new TransactionTemplate(transactionManager);
        concurrentRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> service.execute(key, "op", Map.of("a", 1), Result.class, () -> {
                    concurrentRequest.executeWithoutResult(
                            status -> records.save(new IdempotencyRecord(key, "other-hash", "{\"value\":7}")));
                    return new Result(1);
                }))
                .isInstanceOf(IdempotencyKeyInUseException.class);

        assertThat(records.findById(key)).hasValueSatisfying(record -> assertThat(record.matches("other-hash"))
                .isTrue());
    }

    record Result(int value) {}
}
