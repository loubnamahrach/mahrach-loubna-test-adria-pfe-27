package ma.enset.bank.transaction.service.exception;

public class TransactionRuleException extends RuntimeException {

    public enum Violation {
        INVALID_AMOUNT,
        AMOUNT_LIMIT_EXCEEDED,
        ACCOUNT_NOT_FOUND,
        ACCOUNT_NOT_ACTIVE,
        CURRENCY_MISMATCH,
        INSUFFICIENT_FUNDS,
        SAME_ACCOUNT_TRANSFER,
        IDEMPOTENCY_CONFLICT
    }

    private final Violation violation;

    public TransactionRuleException(Violation violation, String message) {
        super(message);
        this.violation = violation;
    }

    public Violation getViolation() {
        return violation;
    }
}