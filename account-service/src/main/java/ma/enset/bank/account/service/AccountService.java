package ma.enset.bank.account.service;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.domain.Account;
import ma.enset.bank.account.domain.AccountStatus;
import ma.enset.bank.account.dto.AccountResponse;
import ma.enset.bank.account.dto.CreateAccountRequest;
import ma.enset.bank.account.repository.AccountRepository;
import ma.enset.bank.account.service.exception.AccountBlockedException;
import ma.enset.bank.account.service.exception.AccountNotFoundException;
import ma.enset.bank.account.service.exception.InsufficientFundsException;
import ma.enset.bank.account.service.exception.InvalidOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unique point d'entree des regles metier des comptes : REST, GraphQL et MCP delegent ici.
 */
@Service
@Transactional
public class AccountService {

    private static final int MONEY_SCALE = 2;

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public AccountResponse create(CreateAccountRequest request) {
        validateCurrency(request.currency());
        BigDecimal balance = normalize(request.initialBalance(), true);
        Account account = new Account(request.customerReference(), request.currency(), balance);
        return AccountResponse.from(repository.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        return AccountResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list() {
        return repository.findAll().stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID id) {
        return find(id).getBalance();
    }

    public AccountResponse changeStatus(UUID id, AccountStatus status) {
        Account account = find(id);
        account.setStatus(status);
        return AccountResponse.from(account);
    }

    public AccountResponse block(UUID id) {
        return changeStatus(id, AccountStatus.BLOCKED);
    }

    public AccountResponse unblock(UUID id) {
        return changeStatus(id, AccountStatus.ACTIVE);
    }

    public AccountResponse debit(UUID id, BigDecimal amount) {
        BigDecimal value = normalize(amount, false);
        Account account = findActiveForUpdate(id);
        if (account.getBalance().compareTo(value) < 0) {
            throw new InsufficientFundsException(id);
        }
        account.setBalance(account.getBalance().subtract(value));
        return AccountResponse.from(account);
    }

    public AccountResponse credit(UUID id, BigDecimal amount) {
        BigDecimal value = normalize(amount, false);
        Account account = findActiveForUpdate(id);
        account.setBalance(account.getBalance().add(value));
        return AccountResponse.from(account);
    }

    private Account find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private Account findActiveForUpdate(UUID id) {
        Account account = repository.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountBlockedException(id);
        }
        return account;
    }

    private static void validateCurrency(String code) {
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException("Unknown currency code: " + code);
        }
    }

    private static BigDecimal normalize(BigDecimal amount, boolean zeroAllowed) {
        if (amount == null) {
            throw new InvalidOperationException("Amount is required");
        }
        int sign = amount.signum();
        if (sign < 0 || (sign == 0 && !zeroAllowed)) {
            throw new InvalidOperationException(zeroAllowed ? "Amount must not be negative" : "Amount must be strictly positive");
        }
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new InvalidOperationException("Amount must have at most " + MONEY_SCALE + " decimals");
        }
        return amount.setScale(MONEY_SCALE);
    }
}