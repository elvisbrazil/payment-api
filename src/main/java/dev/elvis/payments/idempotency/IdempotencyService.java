package dev.elvis.payments.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyRecordRepository records;
    private final ObjectMapper mapper;

    public IdempotencyService(IdempotencyRecordRepository records, ObjectMapper mapper) {
        this.records = records;
        this.mapper = mapper;
    }

    @Transactional
    public <T> IdempotentResponse<T> execute(
            String key, String operation, Object request, Class<T> responseType, Supplier<T> action) {
        validate(key);
        String hash = hash(operation, request);

        var existing = records.findById(key);
        if (existing.isPresent()) {
            if (!existing.get().matches(hash)) {
                throw new IdempotencyKeyReusedException(key);
            }
            return new IdempotentResponse<>(read(existing.get().responseBody(), responseType), true);
        }

        T response = action.get();
        try {
            records.saveAndFlush(new IdempotencyRecord(key, hash, write(response)));
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyKeyInUseException(key);
        }
        return new IdempotentResponse<>(response, false);
    }

    private static void validate(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidIdempotencyKeyException(HEADER + " header must not be blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException(
                    HEADER + " header must be at most " + MAX_KEY_LENGTH + " characters");
        }
    }

    private String hash(String operation, Object request) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(mapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Could not hash request", e);
        }
    }

    private String write(Object response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize response", e);
        }
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return mapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored response", e);
        }
    }
}
