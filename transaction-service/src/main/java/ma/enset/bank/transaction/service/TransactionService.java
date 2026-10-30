package ma.enset.bank.transaction.service;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import ma.enset.bank.transaction.client.AccountClient;
import ma.enset.bank.transaction.client.AccountView;
import ma.enset.bank.transaction.client.AmountPayload;
import ma.enset.bank.transaction.config.BankTransactionProperties;
import ma.enset.bank.transaction.domain.Transaction;
import ma.enset.bank.transaction.domain.TransferRecord;
import ma.enset.bank.transaction.domain.TransferStatus;
import ma.enset.bank.transaction.dto.CreateTransactionRequest;
import ma.enset.bank.transaction.dto.TransactionResponse;
import ma.enset.bank.transaction.dto.TransferRequest;
import ma.enset.bank.transaction.dto.TransferResponse;
import ma.enset.bank.transaction.repository.TransactionRepository;
import ma.enset.bank.transaction.repository.TransferRecordRepository;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionNotFoundException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException.Violation;
import ma.enset.bank.transaction.service.exception.TransferCompensationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Point d'entree unique des regles metier des transactions et virements (REST, GraphQL et MCP).
 * Protege les appels vers Account-Service par CircuitBreaker et Retry Resilience4J en mode Fail-Closed.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final int MONEY_SCALE = 2;

    private final TransactionRepository repository;
    private final TransferRecordRepository transferRepository;
    private final AccountClient accountClient;
    private final BankTransactionProperties properties;
    private final Clock clock;
    private final CircuitBreaker circuitBreaker;
    private final Retry readRetry;

    public TransactionService(TransactionRepository repository, AccountClient accountClient,
                              BankTransactionProperties properties, Clock clock) {
        this(repository, null, accountClient, properties, clock, defaultCircuitBreaker(), defaultRetry());
    }

    public TransactionService(TransactionRepository repository, TransferRecordRepository transferRepository,
                              AccountClient accountClient, BankTransactionProperties properties, Clock clock) {
        this(repository, transferRepository, accountClient, properties, clock, defaultCircuitBreaker(), defaultRetry());
    }

    @Autowired
    public TransactionService(TransactionRepository repository, TransferRecordRepository transferRepository,
                              AccountClient accountClient, BankTransactionProperties properties, Clock clock,
                              CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this(repository, transferRepository, accountClient, properties, clock,
                circuitBreakerRegistry.circuitBreaker("accountService"),
                retryRegistry.retry("accountServiceRead"));
    }

    public TransactionService(TransactionRepository repository, TransferRecordRepository transferRepository,
                              AccountClient accountClient, BankTransactionProperties properties, Clock clock,
                              CircuitBreaker circuitBreaker, Retry readRetry) {
        this.repository = repository;
        this.transferRepository = transferRepository;
        this.accountClient = accountClient;
        this.properties = properties;
        this.clock = clock;
        this.circuitBreaker = circuitBreaker;
        this.readRetry = readRetry;
    }

    private static CircuitBreaker defaultCircuitBreaker() {
        return CircuitBreaker.of("accountService", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(2)
                .ignoreExceptions(TransactionRuleException.class)
                .build());
    }

    private static Retry defaultRetry() {
        return Retry.of("accountServiceRead", RetryConfig.custom()
                .maxAttempts(1)
                .build());
    }

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

    @Transactional(noRollbackFor = TransferCompensationException.class)
    public TransferResponse transfer(TransferRequest request) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new TransactionRuleException(Violation.SAME_ACCOUNT_TRANSFER,
                    "Source and target accounts must be different");
        }
        BigDecimal amount = validateAmount(request.amount());
        String key = (request.idempotencyKey() == null || request.idempotencyKey().isBlank())
                ? UUID.randomUUID().toString()
                : request.idempotencyKey().trim();

        if (transferRepository != null) {
            Optional<TransferRecord> existing = transferRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                TransferRecord prev = existing.get();
                if (!prev.getSourceAccountId().equals(request.sourceAccountId())
                        || !prev.getTargetAccountId().equals(request.targetAccountId())
                        || prev.getAmount().compareTo(amount) != 0
                        || !prev.getCurrency().equals(request.currency())) {
                    throw new TransactionRuleException(Violation.IDEMPOTENCY_CONFLICT,
                            "Idempotency key already used with different transfer parameters");
                }
                if (prev.getStatus() == TransferStatus.COMPLETED) {
                    return TransferResponse.from(prev);
                }
                throw new TransferCompensationException(prev.getStatus(),
                        "Previous transfer attempt with this idempotency key ended in status " + prev.getStatus(), null);
            }
        }

        AccountView source = fetchAccount(request.sourceAccountId());
        AccountView target = fetchAccount(request.targetAccountId());
        if (!source.isActive()) {
            throw new TransactionRuleException(Violation.ACCOUNT_NOT_ACTIVE, "Source account is not active: " + source.id());
        }
        if (!target.isActive()) {
            throw new TransactionRuleException(Violation.ACCOUNT_NOT_ACTIVE, "Target account is not active: " + target.id());
        }
        if (!source.currency().equals(request.currency()) || !target.currency().equals(request.currency())) {
            throw new TransactionRuleException(Violation.CURRENCY_MISMATCH,
                    "Transfer currency must match both source and target account currencies");
        }
        if (source.balance().compareTo(amount) < 0) {
            throw new TransactionRuleException(Violation.INSUFFICIENT_FUNDS,
                    "Insufficient funds on source account " + source.id());
        }

        Instant now = Instant.now(clock);
        callAccountService(() -> accountClient.debit(source.id(), new AmountPayload(amount)));

        try {
            callAccountService(() -> accountClient.credit(target.id(), new AmountPayload(amount)));
        } catch (RuntimeException creditFailure) {
            log.warn("Credit failed on target account {} after debiting source {}; triggering Saga compensation",
                    target.id(), source.id(), creditFailure);
            TransferStatus status = compensateSource(source.id(), amount);
            if (transferRepository != null) {
                transferRepository.save(new TransferRecord(key, source.id(), target.id(), amount,
                        request.currency(), status, null, creditFailure.getMessage(), now));
            }
            throw new TransferCompensationException(status,
                    status == TransferStatus.COMPENSATED
                            ? "Credit failed; source account was automatically refunded (Saga compensated)"
                            : "Credit failed and automatic refund failed; manual reconciliation required",
                    creditFailure);
        }

        Transaction debitTx = repository.save(new Transaction(source.id(), amount, request.currency(),
                "TRANSFER_OUT", "INTERNAL", now));
        repository.save(new Transaction(target.id(), amount, request.currency(),
                "TRANSFER_IN", "INTERNAL", now));

        TransferRecord record = new TransferRecord(key, source.id(), target.id(), amount,
                request.currency(), TransferStatus.COMPLETED, debitTx.getId(), null, now);
        if (transferRepository != null) {
            record = transferRepository.save(record);
        }
        return TransferResponse.from(record);
    }

    private TransferStatus compensateSource(UUID sourceId, BigDecimal amount) {
        try {
            callAccountService(() -> accountClient.credit(sourceId, new AmountPayload(amount)));
            return TransferStatus.COMPENSATED;
        } catch (RuntimeException compensationError) {
            log.error("CRITICAL: Saga compensation failed for account {} amount {}", sourceId, amount, compensationError);
            return TransferStatus.COMPENSATION_REQUIRED;
        }
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
        Supplier<AccountView> supplier = Retry.decorateSupplier(readRetry,
                () -> callAccountService(() -> accountClient.getAccount(accountId)));
        return supplier.get();
    }

    private <T> T callAccountService(Supplier<T> call) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return call.get();
                } catch (TransactionRuleException e) {
                    throw e;
                } catch (RetryableException e) {
                    throw new AccountServiceUnavailableException("Account-Service timeout or unreachable", e);
                } catch (FeignException e) {
                    throw new AccountServiceUnavailableException("Account-Service communication error", e);
                }
            });
        } catch (CallNotPermittedException e) {
            throw new AccountServiceUnavailableException(
                    "Circuit breaker 'accountService' is OPEN; transaction refused (fail-closed)", e);
        }
    }
}