package ma.enset.bank.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.enset.bank.transaction.domain.TransferRecord;
import ma.enset.bank.transaction.domain.TransferStatus;

public record TransferResponse(
        UUID id,
        String idempotencyKey,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        UUID debitTransactionId,
        Instant createdAt) {

    public static TransferResponse from(TransferRecord record) {
        return new TransferResponse(
                record.getId(),
                record.getIdempotencyKey(),
                record.getSourceAccountId(),
                record.getTargetAccountId(),
                record.getAmount(),
                record.getCurrency(),
                record.getStatus(),
                record.getDebitTransactionId(),
                record.getCreatedAt());
    }
}
