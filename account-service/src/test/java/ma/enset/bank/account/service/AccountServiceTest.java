package ma.enset.bank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    AccountRepository repository;

    AccountService service;
    UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AccountService(repository);
    }

    private Account account(String balance) {
        return new Account("CUST-1", "EUR", new BigDecimal(balance));
    }

    private void existing(Account account) {
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(account));
    }

    @Test
    void createsActiveAccountWithInitialBalance() {
        when(repository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        AccountResponse response = service.create(new CreateAccountRequest("CUST-1", "EUR", new BigDecimal("100")));
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThatThrownBy(() -> service.create(new CreateAccountRequest("C", "ZZZ", BigDecimal.ONE)))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void rejectsNegativeInitialBalance() {
        assertThatThrownBy(() -> service.create(new CreateAccountRequest("C", "EUR", new BigDecimal("-1"))))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void debitReducesBalance() {
        existing(account("100.00"));
        assertThat(service.debit(id, new BigDecimal("30")).balance()).isEqualByComparingTo("70.00");
    }

    @Test
    void creditIncreasesBalance() {
        existing(account("100.00"));
        assertThat(service.credit(id, new BigDecimal("25.50")).balance()).isEqualByComparingTo("125.50");
    }

    @Test
    void debitCannotMakeBalanceNegative() {
        existing(account("10.00"));
        assertThatThrownBy(() -> service.debit(id, new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void debitOfExactBalanceIsAllowed() {
        existing(account("10.00"));
        assertThat(service.debit(id, new BigDecimal("10.00")).balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsZeroNegativeAndTooPreciseAmounts() {
        assertThatThrownBy(() -> service.debit(id, BigDecimal.ZERO)).isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.credit(id, new BigDecimal("-5"))).isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.credit(id, new BigDecimal("1.001"))).isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void blockedAccountRefusesDebitAndCredit() {
        Account blocked = account("100.00");
        blocked.setStatus(AccountStatus.BLOCKED);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(blocked));
        assertThatThrownBy(() -> service.debit(id, BigDecimal.TEN)).isInstanceOf(AccountBlockedException.class);
        assertThatThrownBy(() -> service.credit(id, BigDecimal.TEN)).isInstanceOf(AccountBlockedException.class);
    }

    @Test
    void blockAndUnblockChangeStatus() {
        Account account = account("0.00");
        when(repository.findById(id)).thenReturn(Optional.of(account));
        assertThat(service.block(id).status()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(service.unblock(id).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void unknownAccountIsReported() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getBalance(id)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void returnsBalance() {
        when(repository.findById(id)).thenReturn(Optional.of(account("42.00")));
        assertThat(service.getBalance(id)).isEqualByComparingTo("42.00");
    }
}