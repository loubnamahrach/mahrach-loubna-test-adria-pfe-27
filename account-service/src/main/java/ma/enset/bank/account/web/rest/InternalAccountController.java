package ma.enset.bank.account.web.rest;

import jakarta.validation.Valid;
import java.util.UUID;
import ma.enset.bank.account.dto.AccountResponse;
import ma.enset.bank.account.dto.AmountRequest;
import ma.enset.bank.account.service.AccountService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints internes appeles par Transaction-Service (OpenFeign) pour debiter et crediter.
 */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

    private final AccountService service;

    public InternalAccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/{id}/debit")
    public AccountResponse debit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return service.debit(id, request.amount());
    }

    @PostMapping("/{id}/credit")
    public AccountResponse credit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return service.credit(id, request.amount());
    }
}