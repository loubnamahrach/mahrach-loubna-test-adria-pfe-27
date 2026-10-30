package ma.enset.bank.account.service.exception;

import java.util.UUID;

public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException(UUID id) {
        super("Account is blocked: " + id);
    }
}