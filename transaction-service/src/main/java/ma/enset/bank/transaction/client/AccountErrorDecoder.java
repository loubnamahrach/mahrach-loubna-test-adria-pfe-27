package ma.enset.bank.transaction.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException.Violation;

/**
 * Traduit les reponses d'erreur d'Account-Service. Toute reponse inattendue est traitee comme une
 * indisponibilite : l'operation est alors refusee (fail-closed).
 */
public class AccountErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 404 -> new TransactionRuleException(Violation.ACCOUNT_NOT_FOUND, "Account not found");
            case 409 -> new TransactionRuleException(Violation.ACCOUNT_NOT_ACTIVE, "Account is not active");
            case 422 -> new TransactionRuleException(Violation.INSUFFICIENT_FUNDS, "Insufficient funds");
            case 400 -> new TransactionRuleException(Violation.INVALID_AMOUNT, "Account-Service rejected the request");
            default -> new AccountServiceUnavailableException(
                    "Account-Service answered with status " + response.status(), null);
        };
    }
}