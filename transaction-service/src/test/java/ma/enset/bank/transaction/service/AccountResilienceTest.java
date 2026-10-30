package ma.enset.bank.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;
import ma.enset.bank.transaction.client.AccountClient;
import ma.enset.bank.transaction.client.AccountView;
import ma.enset.bank.transaction.config.BankTransactionProperties;
import ma.enset.bank.transaction.domain.Transaction;
import ma.enset.bank.transaction.dto.CreateTransactionRequest;
import ma.enset.bank.transaction.repository.TransactionRepository;
import ma.enset.bank.transaction.repository.TransferRecordRepository;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException.Violation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountResilienceTest {

    @Mock
    TransactionRepository repository;
    @Mock
    TransferRecordRepository transferRepository;
    @Mock
    AccountClient accountClient;

    CircuitBreaker circuitBreaker;
    Retry readRetry;
    TransactionService service;
    UUID accountId = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-15T10:30:00Z");

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("accountService", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(150))
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .ignoreExceptions(TransactionRuleException.class)
                .build());
        readRetry = Retry.of("accountServiceRead", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(AccountServiceUnavailableException.class)
                .build());
        service = new TransactionService(repository, transferRepository, accountClient,
                new BankTransactionProperties(new BigDecimal("1000")), Clock.fixed(now, ZoneOffset.UTC),
                circuitBreaker, readRetry);
    }

    private CreateTransactionRequest req() {
        return new CreateTransactionRequest(accountId, new BigDecimal("100"), "EUR", "SHOP", "MA");
    }

    private RetryableException timeoutException() {
        Request r = Request.create(Request.HttpMethod.GET, "http://account", new HashMap<>(), null, null, null);
        return new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, r);
    }

    @Test
    void retriesTransientTimeoutAndSucceedsWhenAccountServiceRecovers() {
        when(accountClient.getAccount(accountId))
                .thenThrow(timeoutException())
                .thenReturn(new AccountView(accountId, "CLI", "ACTIVE", "EUR", new BigDecimal("500.00")));
        when(repository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.record(req()).amount()).isEqualByComparingTo("100.00");
        verify(accountClient, times(2)).getAccount(accountId);
    }

    @Test
    void opensCircuitAfterRepeatedFailuresAndRefusesTransactionsThenClosesAfterRecovery() throws Exception {
        when(accountClient.getAccount(accountId)).thenThrow(timeoutException());

        assertThatThrownBy(() -> service.record(req())).isInstanceOf(AccountServiceUnavailableException.class);
        assertThatThrownBy(() -> service.record(req())).isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> service.record(req()))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessageContaining("OPEN");
        verify(repository, never()).save(any());

        Thread.sleep(200);
        circuitBreaker.transitionToHalfOpenState();
        org.mockito.Mockito.reset(accountClient);
        when(accountClient.getAccount(accountId))
                .thenReturn(new AccountView(accountId, "CLI", "ACTIVE", "EUR", new BigDecimal("500.00")));
        when(repository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.record(req()).amount()).isEqualByComparingTo("100.00");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void businessRuleViolationsDoNotOpenCircuitBreaker() {
        when(accountClient.getAccount(accountId))
                .thenThrow(new TransactionRuleException(Violation.ACCOUNT_NOT_FOUND, "not found"));
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> service.record(req())).isInstanceOf(TransactionRuleException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}