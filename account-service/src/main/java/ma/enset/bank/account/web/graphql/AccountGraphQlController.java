package ma.enset.bank.account.web.graphql;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.domain.AccountStatus;
import ma.enset.bank.account.dto.AccountResponse;
import ma.enset.bank.account.dto.CreateAccountRequest;
import ma.enset.bank.account.service.AccountService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
public class AccountGraphQlController {

    private final AccountService service;

    public AccountGraphQlController(AccountService service) {
        this.service = service;
    }

    @QueryMapping
    public List<AccountResponse> accounts() {
        return service.list();
    }

    @QueryMapping
    public AccountResponse account(@Argument UUID id) {
        return service.get(id);
    }

    @QueryMapping
    public BigDecimal accountBalance(@Argument UUID id) {
        return service.getBalance(id);
    }

    @MutationMapping
    public AccountResponse createAccount(@Argument @Valid CreateAccountRequest input) {
        return service.create(input);
    }

    @MutationMapping
    public AccountResponse changeAccountStatus(@Argument UUID id, @Argument AccountStatus status) {
        return service.changeStatus(id, status);
    }

    @MutationMapping
    public AccountResponse blockAccount(@Argument UUID id) {
        return service.block(id);
    }

    @MutationMapping
    public AccountResponse unblockAccount(@Argument UUID id) {
        return service.unblock(id);
    }

    @MutationMapping
    public AccountResponse debitAccount(@Argument UUID id, @Argument BigDecimal amount) {
        return service.debit(id, amount);
    }

    @MutationMapping
    public AccountResponse creditAccount(@Argument UUID id, @Argument BigDecimal amount) {
        return service.credit(id, amount);
    }
}