package ma.enset.bank.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull UUID accountId,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String merchantCategory,
        @NotBlank String country) {
}