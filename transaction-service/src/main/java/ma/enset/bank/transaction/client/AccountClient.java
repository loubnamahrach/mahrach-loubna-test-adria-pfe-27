package ma.enset.bank.transaction.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Seul point d'acces de Transaction-Service aux comptes : jamais d'acces direct a la base d'Account-Service.
 */
@FeignClient(name = "account-service", url = "${clients.account-service.url}", configuration = AccountClientConfig.class)
public interface AccountClient {

    @GetMapping("/api/accounts/{id}")
    AccountView getAccount(@PathVariable("id") UUID id);

    @PostMapping("/internal/accounts/{id}/debit")
    AccountView debit(@PathVariable("id") UUID id, @RequestBody AmountPayload payload);

    @PostMapping("/internal/accounts/{id}/credit")
    AccountView credit(@PathVariable("id") UUID id, @RequestBody AmountPayload payload);
}