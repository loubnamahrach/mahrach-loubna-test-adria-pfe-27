package ma.enset.bank.transaction.repository;

import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}