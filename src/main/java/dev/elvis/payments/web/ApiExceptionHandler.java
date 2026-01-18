package dev.elvis.payments.web;

import dev.elvis.payments.payment.InvalidMoneyException;
import dev.elvis.payments.payment.InvalidPaymentTransitionException;
import dev.elvis.payments.payment.PaymentNotFoundException;
import dev.elvis.payments.payment.RefundExceedsCapturedAmountException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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
    ProblemDetail handle(ObjectOptimisticLockingFailureException e) {
        return problem(
                HttpStatus.CONFLICT, "Concurrent modification", "The payment was modified concurrently, retry the request");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
