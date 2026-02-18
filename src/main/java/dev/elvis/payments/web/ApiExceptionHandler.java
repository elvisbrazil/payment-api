package dev.elvis.payments.web;

import dev.elvis.payments.idempotency.IdempotencyKeyInUseException;
import dev.elvis.payments.idempotency.IdempotencyKeyReusedException;
import dev.elvis.payments.idempotency.InvalidIdempotencyKeyException;
import dev.elvis.payments.payment.InvalidMoneyException;
import dev.elvis.payments.payment.InvalidPaymentTransitionException;
import dev.elvis.payments.payment.PaymentNotFoundException;
import dev.elvis.payments.payment.RefundExceedsCapturedAmountException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = e.getBody();
        problem.setTitle("Invalid request");
        problem.setDetail("One or more fields are invalid");
        problem.setProperty("errors", fieldErrors(e));
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @ExceptionHandler
    ProblemDetail handle(PaymentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Payment not found", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(InvalidPaymentTransitionException e) {
        var problem = problem(HttpStatus.CONFLICT, "Invalid payment state", e.getMessage());
        problem.setProperty("currentStatus", e.from());
        problem.setProperty("attemptedStatus", e.to());
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handle(RefundExceedsCapturedAmountException e) {
        var problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Refund not allowed", e.getMessage());
        problem.setProperty("refundableAmount", e.refundable().amount());
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handle(InvalidMoneyException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid amount", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(IdempotencyKeyInUseException e) {
        return problem(HttpStatus.CONFLICT, "Idempotency key in use", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(InvalidIdempotencyKeyException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid idempotency key", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(ObjectOptimisticLockingFailureException e) {
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "The payment was modified concurrently, retry the request");
    }

    private static Map<String, String> fieldErrors(MethodArgumentNotValidException e) {
        var errors = new TreeMap<String, String>();
        for (FieldError error : e.getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
