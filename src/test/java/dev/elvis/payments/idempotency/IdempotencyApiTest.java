package dev.elvis.payments.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.elvis.payments.payment.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyApiTest {

    private static final String PAYMENT =
            """
            {"amount": 100.00, "currency": "BRL", "cardNumber": "4242424242424242"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaymentRepository payments;

    @Test
    void replaysStoredResponseForSameKeyAndBody() throws Exception {
        var key = newKey();
        long before = payments.count();

        var first = createPayment(key, PAYMENT)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
        var second = createPayment(key, PAYMENT)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(idOf(second)).isEqualTo(idOf(first));
        assertThat(body(second)).isEqualTo(body(first));
        assertThat(payments.count()).isEqualTo(before + 1);
    }

    @Test
    void rejectsSameKeyWithDifferentBody() throws Exception {
        var key = newKey();
        createPayment(key, PAYMENT).andExpect(status().isCreated());

        createPayment(
                        key,
                        """
                        {"amount": 100.01, "currency": "BRL", "cardNumber": "4242424242424242"}
                        """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Idempotency key reused"));
    }

    @Test
    void requiresIdempotencyKey() throws Exception {
        mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(PAYMENT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsBlankKey() throws Exception {
        createPayment("   ", PAYMENT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid idempotency key"));
    }

    @Test
    void replayedRefundDoesNotRefundTwice() throws Exception {
        var paymentId = capturedPayment();
        var key = newKey();

        var first = refund(paymentId, key, "30.00").andExpect(status().isCreated());
        var second = refund(paymentId, key, "30.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(idOf(second)).isEqualTo(idOf(first));
        mvc.perform(get("/payments/{id}", paymentId))
                .andExpect(jsonPath("$.refundedAmount").value(30.00))
                .andExpect(jsonPath("$.refunds.length()").value(1));
    }

    @Test
    void sameKeyOnAnotherPaymentIsRejected() throws Exception {
        var key = newKey();
        refund(capturedPayment(), key, "10.00").andExpect(status().isCreated());

        refund(capturedPayment(), key, "10.00").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void failedRequestDoesNotConsumeKey() throws Exception {
        var paymentId = capturedPayment();
        var key = newKey();

        refund(paymentId, key, "150.00").andExpect(status().isUnprocessableEntity());

        refund(paymentId, key, "50.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
    }

    private ResultActions createPayment(String key, String body) throws Exception {
        return mvc.perform(post("/payments")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions refund(String paymentId, String key, String amount) throws Exception {
        return mvc.perform(post("/payments/{id}/refunds", paymentId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s}
                        """.formatted(amount)));
    }

    private String capturedPayment() throws Exception {
        var id = idOf(createPayment(newKey(), PAYMENT));
        mvc.perform(post("/payments/{id}/capture", id)).andExpect(status().isOk());
        return id;
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
