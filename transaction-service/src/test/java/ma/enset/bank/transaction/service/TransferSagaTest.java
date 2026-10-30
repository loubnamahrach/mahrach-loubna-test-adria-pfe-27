package ma.enset.bank.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import ma.enset.bank.transaction.client.AccountClient;
import ma.enset.bank.transaction.client.AccountView;
import ma.enset.bank.transaction.client.AmountPayload;
import ma.enset.bank.transaction.config.BankTransactionProperties;
import ma.enset.bank.transaction.domain.Transaction;
import ma.enset.bank.transaction.domain.TransferRecord;
import ma.enset.bank.transaction.domain.TransferStatus;
import ma.enset.bank.transaction.dto.TransferRequest;
import ma.enset.bank.transaction.dto.TransferResponse;
import ma.enset.bank.transaction.repository.TransactionRepository;
import ma.enset.bank.transaction.repository.TransferRecordRepository;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException.Violation;
import ma.enset.bank.transaction.service.exception.TransferCompensationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferSagaTest {

    @Mock
    TransactionRepository transactionRepository;
    @Mock
    TransferRecordRepository transferRepository;
    @Mock
    AccountClient accountClient;

    TransactionService service;
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-15T10:30:00Z");

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, transferRepository, accountClient,
                new BankTransactionProperties(new BigDecimal("1000")), Clock.fixed(now, ZoneOffset.UTC));
    }

    private AccountView account(UUID id, String status, String currency, String balance) {
        return new AccountView(id, "CUST", status, currency, new BigDecimal(balance));
    }

    @Test
    void executesTransferDebitingSourceThenCreditingTarget() {
        when(transferRepository.findByIdempotencyKey("KEY-1")).thenReturn(Optional.empty());
        when(accountClient.getAccount(sourceId)).thenReturn(account(sourceId, "ACTIVE", "EUR", "500.00"));
        when(accountClient.getAccount(targetId)).thenReturn(account(targetId, "ACTIVE", "EUR", "200.00"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(transferRepository.save(any(TransferRecord.class))).thenAnswer(i -> i.getArgument(0));

        TransferResponse response = service.transfer(
                new TransferRequest(sourceId, targetId, new BigDecimal("100"), "EUR", "KEY-1"));

        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        var order = inOrder(accountClient, transactionRepository, transferRepository);
        order.verify(accountClient).debit(sourceId, new AmountPayload(new BigDecimal("100.00")));
        order.verify(accountClient).credit(targetId, new AmountPayload(new BigDecimal("100.00")));
        order.verify(transactionRepository, times(2)).save(any(Transaction.class));
        order.verify(transferRepository).save(any(TransferRecord.class));
    }

    @Test
    void compensatesSourceWhenCreditTargetFails() {
        when(transferRepository.findByIdempotencyKey("KEY-FAIL")).thenReturn(Optional.empty());
        when(accountClient.getAccount(sourceId)).thenReturn(account(sourceId, "ACTIVE", "EUR", "500.00"));
        when(accountClient.getAccount(targetId)).thenReturn(account(targetId, "ACTIVE", "EUR", "200.00"));
        when(accountClient.credit(eq(targetId), any()))
                .thenThrow(new AccountServiceUnavailableException("timeout during credit", null));
        when(transferRepository.save(any(TransferRecord.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.transfer(
                new TransferRequest(sourceId, targetId, new BigDecimal("100"), "EUR", "KEY-FAIL")))
                .isInstanceOfSatisfying(TransferCompensationException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(TransferStatus.COMPENSATED));

        // Verification de la compensation automatique (re-credit de la source)
        verify(accountClient).debit(sourceId, new AmountPayload(new BigDecimal("100.00")));
        verify(accountClient).credit(sourceId, new AmountPayload(new BigDecimal("100.00")));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void idempotentReplayReturnsExistingCompletedTransferWithoutChargingAgain() {
        TransferRecord existing = new TransferRecord("KEY-DUP", sourceId, targetId,
                new BigDecimal("100.00"), "EUR", TransferStatus.COMPLETED, UUID.randomUUID(), null, now);
        when(transferRepository.findByIdempotencyKey("KEY-DUP")).thenReturn(Optional.of(existing));

        TransferResponse response = service.transfer(
                new TransferRequest(sourceId, targetId, new BigDecimal("100"), "EUR", "KEY-DUP"));

        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        verify(accountClient, never()).debit(any(), any());
        verify(accountClient, never()).credit(any(), any());
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentAmount() {
        TransferRecord existing = new TransferRecord("KEY-DUP", sourceId, targetId,
                new BigDecimal("100.00"), "EUR", TransferStatus.COMPLETED, UUID.randomUUID(), null, now);
        when(transferRepository.findByIdempotencyKey("KEY-DUP")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.transfer(
                new TransferRequest(sourceId, targetId, new BigDecimal("250"), "EUR", "KEY-DUP")))
                .isInstanceOfSatisfying(TransactionRuleException.class,
                        e -> assertThat(e.getViolation()).isEqualTo(Violation.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void rejectsTransferToSameAccount() {
        assertThatThrownBy(() -> service.transfer(
                new TransferRequest(sourceId, sourceId, new BigDecimal("50"), "EUR", "K")))
                .isInstanceOfSatisfying(TransactionRuleException.class,
                        e -> assertThat(e.getViolation()).isEqualTo(Violation.SAME_ACCOUNT_TRANSFER));
    }

    @Test
    void rejectsTransferWhenTargetIsBlockedOrCurrencyMismatches() {
        when(transferRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(accountClient.getAccount(sourceId)).thenReturn(account(sourceId, "ACTIVE", "EUR", "500.00"));
        when(accountClient.getAccount(targetId)).thenReturn(account(targetId, "BLOCKED", "EUR", "200.00"));

        assertThatThrownBy(() -> service.transfer(
                new TransferRequest(sourceId, targetId, new BigDecimal("50"), "EUR", "K1")))
                .isInstanceOfSatisfying(TransactionRuleException.class,
                        e -> assertThat(e.getViolation()).isEqualTo(Violation.ACCOUNT_NOT_ACTIVE));

        when(accountClient.getAccount(targetId)).thenReturn(account(targetId, "ACTIVE", "USD", "200.00"));
        assertThatThrownBy(() -> service.transfer(
                new TransferRequest(sourceId, targetId, new BigDecimal("50"), "EUR", "K2")))
                .isInstanceOfSatisfying(TransactionRuleException.class,
                        e -> assertThat(e.getViolation()).isEqualTo(Violation.CURRENCY_MISMATCH));
    }
}