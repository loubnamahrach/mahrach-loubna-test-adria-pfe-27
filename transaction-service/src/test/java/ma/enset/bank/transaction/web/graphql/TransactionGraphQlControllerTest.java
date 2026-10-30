package ma.enset.bank.transaction.web.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.domain.TransferStatus;
import ma.enset.bank.transaction.dto.TransactionResponse;
import ma.enset.bank.transaction.dto.TransferResponse;
import ma.enset.bank.transaction.service.TransactionService;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException.Violation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(TransactionGraphQlController.class)
@Import({GraphQlConfig.class, GraphQlExceptionResolver.class})
class TransactionGraphQlControllerTest {

    @Autowired
    GraphQlTester tester;

    @MockitoBean
    TransactionService service;

    UUID id = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-15T10:30:00Z");

    TransactionResponse sample() {
        return new TransactionResponse(id, accountId, new BigDecimal("250.00"), "EUR", "GROCERY", "MA", now);
    }

    @Test
    void queriesTransactionsAndByAccount() {
        when(service.list()).thenReturn(List.of(sample()));
        when(service.listByAccount(accountId)).thenReturn(List.of(sample()));
        tester.document("{ transactions { id currency amount } }").execute()
                .path("transactions[0].currency").entity(String.class).isEqualTo("EUR");
        tester.document("{ transactionsByAccount(accountId: \"" + accountId + "\") { id country } }").execute()
                .path("transactionsByAccount[0].country").entity(String.class).isEqualTo("MA");
    }

    @Test
    void recordsTransactionMutation() {
        when(service.record(any())).thenReturn(sample());
        tester.document("mutation { recordTransaction(input: {accountId: \"" + accountId
                        + "\", amount: 250, currency: \"EUR\", merchantCategory: \"GROCERY\", country: \"MA\"}) { id amount } }")
                .execute()
                .path("recordTransaction.amount").entity(BigDecimal.class).isEqualTo(new BigDecimal("250.00"));
    }

    @Test
    void executesTransferMutation() {
        TransferResponse resp = new TransferResponse(UUID.randomUUID(), "K1", accountId, targetId,
                new BigDecimal("100.00"), "EUR", TransferStatus.COMPLETED, id, now);
        when(service.transfer(any())).thenReturn(resp);
        tester.document("mutation { transferFunds(input: {sourceAccountId: \"" + accountId
                        + "\", targetAccountId: \"" + targetId
                        + "\", amount: 100, currency: \"EUR\", idempotencyKey: \"K1\"}) { status amount } }")
                .execute()
                .path("transferFunds.status").entity(String.class).isEqualTo("COMPLETED");
    }

    @Test
    void mapsBusinessViolationsToGraphQlErrors() {
        when(service.record(any())).thenThrow(new TransactionRuleException(Violation.AMOUNT_LIMIT_EXCEEDED, "limit"));
        tester.document("mutation { recordTransaction(input: {accountId: \"" + accountId
                        + "\", amount: 5000, currency: \"EUR\", merchantCategory: \"GROCERY\", country: \"MA\"}) { id } }")
                .execute()
                .errors().expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST);
    }
}