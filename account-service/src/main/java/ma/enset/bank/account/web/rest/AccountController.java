package ma.enset.bank.account.web.rest;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.dto.AccountResponse;
import ma.enset.bank.account.dto.CreateAccountRequest;
import ma.enset.bank.account.dto.StatusRequest;
import ma.enset.bank.account.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<AccountResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/balance")
    public BigDecimal balance(@PathVariable UUID id) {
        return service.getBalance(id);
    }

    @PatchMapping("/{id}/status")
    public AccountResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @PostMapping("/{id}/block")
    public AccountResponse block(@PathVariable UUID id) {
        return service.block(id);
    }

    @PostMapping("/{id}/unblock")
    public AccountResponse unblock(@PathVariable UUID id) {
        return service.unblock(id);
    }
}