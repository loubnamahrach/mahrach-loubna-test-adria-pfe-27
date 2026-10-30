package ma.enset.bank.account.dto;

import java.math.BigDecimal;
import java.util.UUID;
import ma.enset.bank.account.domain.Account;
import ma.enset.bank.account.domain.AccountStatus;

public record AccountResponse(
        UUID id, String customerReference, AccountStatus status, String currency, BigDecimal balance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getCustomerReference(), account.getStatus(),
                account.getCurrency(), account.getBalance());
    }
}