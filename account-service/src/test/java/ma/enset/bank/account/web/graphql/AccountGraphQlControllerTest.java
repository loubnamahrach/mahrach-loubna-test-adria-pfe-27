package ma.enset.bank.account.web.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.domain.AccountStatus;
import ma.enset.bank.account.dto.AccountResponse;
import ma.enset.bank.account.service.AccountService;
import ma.enset.bank.account.service.exception.AccountNotFoundException;
import ma.enset.bank.account.service.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(AccountGraphQlController.class)
@Import({GraphQlConfig.class, GraphQlExceptionResolver.class})
class AccountGraphQlControllerTest {

    @Autowired
    GraphQlTester tester;

    @MockitoBean
    AccountService service;

    UUID id = UUID.randomUUID();

    private AccountResponse sample(AccountStatus status, String balance) {
        return new AccountResponse(id, "CUST-1", status, "EUR", new BigDecimal(balance));
    }

    @Test
    void queriesAccounts() {
        when(service.list()).thenReturn(List.of(sample(AccountStatus.ACTIVE, "100.00")));
        tester.document("{ accounts { id currency status balance } }").execute()
                .path("accounts[0].currency").entity(String.class).isEqualTo("EUR")
                .path("accounts[0].status").entity(String.class).isEqualTo("ACTIVE");
    }

    @Test
    void queriesBalance() {
        when(service.getBalance(id)).thenReturn(new BigDecimal("42.50"));
        tester.document("{ accountBalance(id: \"" + id + "\") }").execute()
                .path("accountBalance").entity(BigDecimal.class).isEqualTo(new BigDecimal("42.50"));
    }

    @Test
    void createsAccount() {
        when(service.create(any())).thenReturn(sample(AccountStatus.ACTIVE, "100.00"));
        tester.document("mutation { createAccount(input: {customerReference: \"CUST-1\", currency: \"EUR\", "
                        + "initialBalance: 100}) { id status balance } }").execute()
                .path("createAccount.status").entity(String.class).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsInvalidInputThroughValidation() {
        tester.document("mutation { createAccount(input: {customerReference: \"\", currency: \"euro\", "
                        + "initialBalance: -5}) { id } }").execute()
                .errors().expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST);
    }

    @Test
    void blocksAndUnblocks() {
        when(service.block(id)).thenReturn(sample(AccountStatus.BLOCKED, "10.00"));
        when(service.unblock(id)).thenReturn(sample(AccountStatus.ACTIVE, "10.00"));
        tester.document("mutation { blockAccount(id: \"" + id + "\") { status } }").execute()
                .path("blockAccount.status").entity(String.class).isEqualTo("BLOCKED");
        tester.document("mutation { unblockAccount(id: \"" + id + "\") { status } }").execute()
                .path("unblockAccount.status").entity(String.class).isEqualTo("ACTIVE");
    }

    @Test
    void debitsAndCredits() {
        when(service.debit(id, new BigDecimal("30"))).thenReturn(sample(AccountStatus.ACTIVE, "70.00"));
        when(service.credit(id, new BigDecimal("5"))).thenReturn(sample(AccountStatus.ACTIVE, "75.00"));
        tester.document("mutation { debitAccount(id: \"" + id + "\", amount: 30) { balance } }").execute()
                .path("debitAccount.balance").entity(BigDecimal.class).isEqualTo(new BigDecimal("70.00"));
        tester.document("mutation { creditAccount(id: \"" + id + "\", amount: 5) { balance } }").execute()
                .path("creditAccount.balance").entity(BigDecimal.class).isEqualTo(new BigDecimal("75.00"));
    }

    @Test
    void mapsBusinessErrors() {
        when(service.get(id)).thenThrow(new AccountNotFoundException(id));
        tester.document("{ account(id: \"" + id + "\") { id } }").execute()
                .errors().expect(e -> e.getErrorType() == ErrorType.NOT_FOUND);
        when(service.debit(any(), any())).thenThrow(new InsufficientFundsException(id));
        tester.document("mutation { debitAccount(id: \"" + id + "\", amount: 999) { id } }").execute()
                .errors().expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST);
    }
}