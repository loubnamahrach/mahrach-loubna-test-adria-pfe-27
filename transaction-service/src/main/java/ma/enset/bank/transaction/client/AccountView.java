package ma.enset.bank.transaction.client;

import java.math.BigDecimal;
import java.util.UUID;

/** Vue d'un compte telle que retournee par Account-Service. */
public record AccountView(UUID id, String customerReference, String status, String currency, BigDecimal balance) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}