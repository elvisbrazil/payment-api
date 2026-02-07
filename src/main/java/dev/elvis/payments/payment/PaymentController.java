package dev.elvis.payments.payment;

import dev.elvis.payments.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/payments")
class PaymentController {

    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final PaymentService service;
    private final IdempotencyService idempotency;

    PaymentController(PaymentService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping
    ResponseEntity<PaymentResponse> create(
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            UriComponentsBuilder uri) {
        var result = idempotency.execute(
                idempotencyKey,
                "create-payment",
                request,
                PaymentResponse.class,
                () -> PaymentResponse.from(service.create(request.money(), request.cardNumber())));
        var location =
                uri.path("/payments/{id}").buildAndExpand(result.body().id()).toUri();
        return ResponseEntity.created(location)
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.body());
    }

    @GetMapping("/{id}")
    PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(service.get(id));
    }

    @PostMapping("/{id}/capture")
    PaymentResponse capture(@PathVariable UUID id) {
        return PaymentResponse.from(service.capture(id));
    }

    @PostMapping("/{id}/void")
    PaymentResponse voidPayment(@PathVariable UUID id) {
        return PaymentResponse.from(service.voidAuthorization(id));
    }

    @PostMapping("/{id}/refunds")
    ResponseEntity<RefundResponse> refund(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody RefundRequest request) {
        var result = idempotency.execute(
                idempotencyKey,
                "refund-payment:" + id,
                request,
                RefundResponse.class,
                () -> RefundResponse.from(service.refund(id, request.amount())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.body());
    }
}
