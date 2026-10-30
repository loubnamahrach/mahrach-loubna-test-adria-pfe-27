package ma.enset.bank.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Trace persistante d'un virement inter-comptes avec cle d'idempotence et etat de la Saga.
 */
@Entity
@Table(name = "transfers")
public class TransferRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID sourceAccountId;

    @Column(nullable = false)
    private UUID targetAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private TransferStatus status;

    @Column
    private UUID debitTransactionId;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    protected TransferRecord() {
    }

    public TransferRecord(String idempotencyKey, UUID sourceAccountId, UUID targetAccountId,
                          BigDecimal amount, String currency, TransferStatus status,
                          UUID debitTransactionId, String failureReason, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.debitTransactionId = debitTransactionId;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public UUID getDebitTransactionId() {
        return debitTransactionId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
