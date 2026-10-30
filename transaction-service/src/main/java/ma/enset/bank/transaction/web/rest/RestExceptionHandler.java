package ma.enset.bank.transaction.web.rest;

import java.util.stream.Collectors;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionNotFoundException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransferCompensationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(TransactionRuleException.class)
    ProblemDetail rule(TransactionRuleException e) {
        HttpStatus status = switch (e.getViolation()) {
            case INVALID_AMOUNT, SAME_ACCOUNT_TRANSFER -> HttpStatus.BAD_REQUEST;
            case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCOUNT_NOT_ACTIVE, IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case AMOUNT_LIMIT_EXCEEDED, CURRENCY_MISMATCH, INSUFFICIENT_FUNDS -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("violation", e.getViolation().name());
        return problem;
    }

    @ExceptionHandler(TransferCompensationException.class)
    ProblemDetail transferCompensated(TransferCompensationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setProperty("transferStatus", e.getStatus().name());
        return problem;
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ProblemDetail notFound(TransactionNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    ProblemDetail unavailable(AccountServiceUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Account verification failed, the transaction was refused: " + e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail malformed(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details);
    }
}