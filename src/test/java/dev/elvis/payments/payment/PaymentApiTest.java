package dev.elvis.payments.payment;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class PaymentApiTest {

    private static final String APPROVED_CARD = "4242424242424242";
    private static final String DECLINED_CARD = "4000000000000002";

    @Autowired
    private MockMvc mvc;

    @Test
    void createsAuthorizedPayment() throws Exception {
        createPayment("150.00", "BRL", APPROVED_CARD)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/payments/")))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.cardLast4").value("4242"))
                .andExpect(jsonPath("$.authorizationCode").exists())
                .andExpect(jsonPath("$.cardNumber").doesNotExist());
    }

    @Test
    void createsDeclinedPaymentForDeclinedCard() throws Exception {
        createPayment("150.00", "BRL", DECLINED_CARD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("card_declined"))
                .andExpect(jsonPath("$.authorizationCode").doesNotExist());
    }

    @Test
    void returnsStoredPayment() throws Exception {
        var id = idOf(createPayment("80.00", "USD", APPROVED_CARD));

        mvc.perform(get("/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));
    }

    @Test
    void returnsNotFoundProblemForUnknownPayment() throws Exception {
        mvc.perform(get("/payments/{id}", "7d3c5d1e-8f43-4f39-9d5c-2b0c2a7f6c11"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Payment not found"));
    }

    @Test
    void capturesAndRefundsPartially() throws Exception {
        var id = idOf(createPayment("100.00", "BRL", APPROVED_CARD));

        mvc.perform(post("/payments/{id}/capture", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));

        refund(id, "40.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(id))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.currency").value("BRL"));

        mvc.perform(get("/payments/{id}", id))
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.refundedAmount").value(40.00))
                .andExpect(jsonPath("$.refunds.length()").value(1));
    }

    @Test
    void refundsRemainingAmount() throws Exception {
        var id = capturedPayment("100.00");

        refund(id, "40.00").andExpect(status().isCreated());
        refund(id, "60.00").andExpect(status().isCreated());

        mvc.perform(get("/payments/{id}", id)).andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void rejectsRefundAboveCapturedAmount() throws Exception {
        var id = capturedPayment("100.00");
        refund(id, "70.00").andExpect(status().isCreated());

        refund(id, "30.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.refundableAmount").value(30.00));
    }

    @Test
    void rejectsCaptureOfDeclinedPayment() throws Exception {
        var id = idOf(createPayment("100.00", "BRL", DECLINED_CARD));

        mvc.perform(post("/payments/{id}/capture", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("DECLINED"))
                .andExpect(jsonPath("$.attemptedStatus").value("CAPTURED"));
    }

    @Test
    void rejectsVoidAfterCapture() throws Exception {
        var id = capturedPayment("25.00");

        mvc.perform(post("/payments/{id}/void", id)).andExpect(status().isConflict());
    }

    @Test
    void voidsAuthorizedPayment() throws Exception {
        var id = idOf(createPayment("25.00", "BRL", APPROVED_CARD));

        mvc.perform(post("/payments/{id}/void", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
    }

    @Test
    void rejectsInvalidPaymentRequest() throws Exception {
        mvc.perform(
                        post("/payments")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"amount": -5, "currency": "brl", "cardNumber": "12"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.amount[0]").value("must be greater than 0"))
                .andExpect(jsonPath("$.errors.currency[0]").value("must be an ISO 4217 code"))
                .andExpect(jsonPath("$.errors.cardNumber", hasItem("must contain 12 to 19 digits")));
    }

    @Test
    void rejectsCardNumberFailingLuhnCheck() throws Exception {
        createPayment("10.00", "BRL", "4242424242424241")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cardNumber[0]").value("is not a valid card number"));
    }

    @Test
    void rejectsUnknownCurrency() throws Exception {
        createPayment("10.00", "XYZ", APPROVED_CARD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown currency: XYZ"));
    }

    @Test
    void rejectsTooManyDecimalPlacesForCurrency() throws Exception {
        createPayment("10.5", "JPY", APPROVED_CARD).andExpect(status().isBadRequest());
    }

    private ResultActions createPayment(String amount, String currency, String cardNumber) throws Exception {
        return mvc.perform(post("/payments")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"amount": %s, "currency": "%s", "cardNumber": "%s"}
                        """
                                .formatted(amount, currency, cardNumber)));
    }

    private ResultActions refund(String paymentId, String amount) throws Exception {
        return mvc.perform(post("/payments/{id}/refunds", paymentId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s}
                        """.formatted(amount)));
    }

    private String capturedPayment(String amount) throws Exception {
        var id = idOf(createPayment(amount, "BRL", APPROVED_CARD));
        mvc.perform(post("/payments/{id}/capture", id)).andExpect(status().isOk());
        return id;
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
