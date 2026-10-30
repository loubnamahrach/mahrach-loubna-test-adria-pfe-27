package ma.enset.bank.account.service.exception;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID id) {
        super("Insufficient funds on account: " + id);
    }
}