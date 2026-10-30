package ma.enset.bank.account.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AmountRequest(@NotNull BigDecimal amount) {
}