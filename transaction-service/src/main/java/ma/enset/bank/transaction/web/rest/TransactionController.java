package ma.enset.bank.transaction.web.rest;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.dto.CreateTransactionRequest;
import ma.enset.bank.transaction.dto.TransactionResponse;
import ma.enset.bank.transaction.dto.TransferRequest;
import ma.enset.bank.transaction.dto.TransferResponse;
import ma.enset.bank.transaction.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse record(@Valid @RequestBody CreateTransactionRequest request) {
        return service.record(request);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String headerKey,
            @Valid @RequestBody TransferRequest request) {
        String effectiveKey = (headerKey != null && !headerKey.isBlank()) ? headerKey : request.idempotencyKey();
        return service.transfer(new TransferRequest(
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amount(),
                request.currency(),
                effectiveKey));
    }

    @GetMapping
    public List<TransactionResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/account/{accountId}")
    public List<TransactionResponse> listByAccount(@PathVariable UUID accountId) {
        return service.listByAccount(accountId);
    }
}