package ma.enset.bank.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    TransactionRepository repository;
    @Mock
    AccountClient accountClient;

    TransactionService service;
    UUID accountId = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-15T10:30:00Z");

    @BeforeEach
    void setUp() {
        service = new TransactionService(repository, accountClient,
                new BankTransactionProperties(new BigDecimal("1000")), Clock.fixed(now, ZoneOffset.UTC));
    }

    private AccountView account(String status, String currency, String balance) {
        return new AccountView(accountId, "CUST-1", status, currency, new BigDecimal(balance));
    }

    private CreateTransactionRequest request(String amount, String currency) {
        return new CreateTransactionRequest(accountId, new BigDecimal(amount), currency, "GROCERY", "MA");
    }

    private void accountReturns(AccountView view) {
        when(accountClient.getAccount(accountId)).thenReturn(view);
    }

    private void assertViolation(CreateTransactionRequest request, Violation expected) {
        assertThatThrownBy(() -> service.record(request))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getViolation()).isEqualTo(expected));
        verify(repository, never()).save(any());
        verify(accountClient, never()).debit(any(), any());
    }

    @Test
    void recordsValidTransactionThenDebitsAccountInOrder() {
        accountReturns(account("ACTIVE", "EUR", "500.00"));
        when(repository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        TransactionResponse response = service.record(request("250", "EUR"));

        assertThat(response.amount()).isEqualByComparingTo("250.00");
        assertThat(response.createdAt()).isEqualTo(now);
        var order = inOrder(accountClient, repository);
        order.verify(accountClient).getAccount(accountId);
        order.verify(repository).save(any(Transaction.class));
        order.verify(accountClient).debit(accountId, new AmountPayload(new BigDecimal("250.00")));
    }

    @Test
    void rejectsZeroAndNegativeAmountsWithoutCallingAccountService() {
        assertViolation(request("0", "EUR"), Violation.INVALID_AMOUNT);
        assertViolation(request("-10", "EUR"), Violation.INVALID_AMOUNT);
        verifyNoInteractions(accountClient);
    }

    @Test
    void rejectsAmountWithMoreThanTwoDecimals() {
        assertViolation(request("1.005", "EUR"), Violation.INVALID_AMOUNT);
    }

    @Test
    void rejectsAmountAboveConfiguredLimit() {
        assertViolation(request("1000.01", "EUR"), Violation.AMOUNT_LIMIT_EXCEEDED);
        verifyNoInteractions(accountClient);
    }

    @Test
    void acceptsAmountEqualToLimit() {
        accountReturns(account("ACTIVE", "EUR", "5000.00"));
        when(repository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.record(request("1000", "EUR")).amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void limitComesFromConfiguration() {
        service = new TransactionService(repository, accountClient,
                new BankTransactionProperties(new BigDecimal("100")), Clock.fixed(now, ZoneOffset.UTC));
        assertViolation(request("150", "EUR"), Violation.AMOUNT_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsBlockedAccount() {
        accountReturns(account("BLOCKED", "EUR", "500.00"));
        assertViolation(request("10", "EUR"), Violation.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void rejectsCurrencyMismatch() {
        accountReturns(account("ACTIVE", "EUR", "500.00"));
        assertViolation(request("10", "USD"), Violation.CURRENCY_MISMATCH);
    }

    @Test
    void rejectsInsufficientBalance() {
        accountReturns(account("ACTIVE", "EUR", "5.00"));
        assertViolation(request("10", "EUR"), Violation.INSUFFICIENT_FUNDS);
    }

    @Test
    void unknownAccountIsPropagatedAsBusinessError() {
        when(accountClient.getAccount(accountId))
                .thenThrow(new TransactionRuleException(Violation.ACCOUNT_NOT_FOUND, "Account not found"));
        assertViolation(request("10", "EUR"), Violation.ACCOUNT_NOT_FOUND);
    }

    private Request feignRequest() {
        return Request.create(Request.HttpMethod.GET, "http://account", new HashMap<>(), null, null, null);
    }

    @Test
    void unreachableAccountServiceRefusesTransaction() {
        when(accountClient.getAccount(accountId))
                .thenThrow(new RetryableException(-1, "refused", Request.HttpMethod.GET, (Long) null, feignRequest()));
        assertThatThrownBy(() -> service.record(request("10", "EUR")))
                .isInstanceOf(AccountServiceUnavailableException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void unexpectedFeignErrorRefusesTransaction() {
        when(accountClient.getAccount(accountId))
                .thenThrow(new FeignException.InternalServerError("boom", feignRequest(), null, null));
        assertThatThrownBy(() -> service.record(request("10", "EUR")))
                .isInstanceOf(AccountServiceUnavailableException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void failedDebitIsPropagatedSoThatLocalSaveRollsBack() {
        accountReturns(account("ACTIVE", "EUR", "500.00"));
        when(repository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(accountClient.debit(any(), any()))
                .thenThrow(new TransactionRuleException(Violation.INSUFFICIENT_FUNDS, "Insufficient funds"));
        assertThatThrownBy(() -> service.record(request("10", "EUR"))).isInstanceOf(TransactionRuleException.class);
    }

    @Test
    void unknownTransactionIsReported() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(TransactionNotFoundException.class);
    }
}