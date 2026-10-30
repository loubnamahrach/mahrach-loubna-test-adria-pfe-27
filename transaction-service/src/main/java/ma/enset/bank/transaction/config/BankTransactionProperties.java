package ma.enset.bank.transaction.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parametres metier externalises (bank.transaction.*), fournis par la configuration centralisee.
 */
@Validated
@ConfigurationProperties(prefix = "bank.transaction")
public record BankTransactionProperties(@NotNull @DecimalMin(value = "0.01") BigDecimal maxAmount) {
}