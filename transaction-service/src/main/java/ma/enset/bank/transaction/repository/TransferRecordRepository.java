package ma.enset.bank.transaction.repository;

import java.util.Optional;
import java.util.UUID;
import ma.enset.bank.transaction.domain.TransferRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRecordRepository extends JpaRepository<TransferRecord, UUID> {

    Optional<TransferRecord> findByIdempotencyKey(String idempotencyKey);
}
