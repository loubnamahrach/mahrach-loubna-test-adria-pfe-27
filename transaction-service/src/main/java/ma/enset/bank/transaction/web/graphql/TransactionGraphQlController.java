package ma.enset.bank.transaction.web.graphql;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.dto.CreateTransactionRequest;
import ma.enset.bank.transaction.dto.TransactionResponse;
import ma.enset.bank.transaction.dto.TransferRequest;
import ma.enset.bank.transaction.dto.TransferResponse;
import ma.enset.bank.transaction.service.TransactionService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
public class TransactionGraphQlController {

    private final TransactionService service;

    public TransactionGraphQlController(TransactionService service) {
        this.service = service;
    }

    @QueryMapping
    public List<TransactionResponse> transactions() {
        return service.list();
    }

    @QueryMapping
    public TransactionResponse transaction(@Argument UUID id) {
        return service.get(id);
    }

    @QueryMapping
    public List<TransactionResponse> transactionsByAccount(@Argument UUID accountId) {
        return service.listByAccount(accountId);
    }

    @MutationMapping
    public TransactionResponse recordTransaction(@Argument @Valid CreateTransactionRequest input) {
        return service.record(input);
    }

    @MutationMapping
    public TransferResponse transferFunds(@Argument @Valid TransferRequest input) {
        return service.transfer(input);
    }
}