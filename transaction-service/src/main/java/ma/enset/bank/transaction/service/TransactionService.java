package ma.enset.bank.transaction.service;

import feign.FeignException;
import feign.RetryableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.client.AccountClient;
import ma.enset.bank.transaction.client.AccountView;
import ma.enset.bank.transaction.client.AmountPayload;
import ma.enset.bank.transaction.config.BankTransactionProperties;
import ma.enset.bank.transaction.domain.Transaction;
import ma.enset.bank.transaction.dto.CreateTransactionRequest;
import ma.enset.bank.transaction.dto.TransactionResponse;
import ma.enset.bank.transaction.repository.TransactionRepository;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionNotFoundException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException.Violation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Point d'entree unique des regles metier des transactions (REST, GraphQL et MCP).
 * Une transaction enregistree represente un debit du compte.
 */
@Service
public class TransactionService {

    private static final int MONEY_SCALE = 2;

    private final TransactionRepository repository;
    private final AccountClient accountClient;
    private final BankTransactionProperties properties;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, AccountClient accountClient,
                              BankTransactionProperties properties, Clock clock) {
        this.repository = repository;
        this.accountClient = accountClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Si le debit echoue apres l'enregistrement, l'exception annule la transaction locale (rollback).
     * Un echec du commit local apres un debit reussi ne peut pas etre annule : cette limite est documentee.
     */
    @Transactional
    public TransactionResponse record(CreateTransactionRequest request) {
        BigDecimal amount = validateAmount(request.amount());
        AccountView account = fetchAccount(request.accountId());
        if (!account.isActive()) {
            throw new TransactionRuleException(Violation.ACCOUNT_NOT_ACTIVE, "Account is not active: " + account.id());
        }
        if (!account.currency().equals(request.currency())) {
            throw new TransactionRuleException(Violation.CURRENCY_MISMATCH,
                    "Currency " + request.currency() + " does not match account currency " + account.currency());
        }
        if (account.balance().compareTo(amount) < 0) {
            throw new TransactionRuleException(Violation.INSUFFICIENT_FUNDS, "Insufficient funds on account " + account.id());
        }
        Transaction saved = repository.save(new Transaction(account.id(), amount, request.currency(),
                request.merchantCategory(), request.country(), Instant.now(clock)));
        callAccountService(() -> accountClient.debit(account.id(), new AmountPayload(amount)));
        return TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id) {
        return repository.findById(id).map(TransactionResponse::from)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list() {
        return repository.findAll().stream().map(TransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listByAccount(UUID accountId) {
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(TransactionResponse::from).toList();
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new TransactionRuleException(Violation.INVALID_AMOUNT, "Amount must be strictly positive");
        }
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new TransactionRuleException(Violation.INVALID_AMOUNT,
                    "Amount must have at most " + MONEY_SCALE + " decimals");
        }
        if (amount.compareTo(properties.maxAmount()) > 0) {
            throw new TransactionRuleException(Violation.AMOUNT_LIMIT_EXCEEDED,
                    "Amount exceeds the maximum of " + properties.maxAmount());
        }
        return amount.setScale(MONEY_SCALE);
    }

    private AccountView fetchAccount(UUID accountId) {
        return callAccountService(() -> accountClient.getAccount(accountId));
    }

    private <T> T callAccountService(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (TransactionRuleException e) {
            throw e;
        } catch (RetryableException e) {
            throw new AccountServiceUnavailableException("Account-Service is unreachable", e);
        } catch (FeignException e) {
            throw new AccountServiceUnavailableException("Account-Service call failed", e);
        }
    }
}