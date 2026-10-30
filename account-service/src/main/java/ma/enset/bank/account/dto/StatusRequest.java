package ma.enset.bank.account.dto;

import jakarta.validation.constraints.NotNull;
import ma.enset.bank.account.domain.AccountStatus;

public record StatusRequest(@NotNull AccountStatus status) {
}