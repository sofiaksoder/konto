package dev.konto.ledger;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByIdDesc(Long accountId, Pageable pageable);

    @Query("""
            select coalesce(sum(case when e.direction = dev.konto.ledger.Direction.CREDIT
                                     then e.amount else -e.amount end), 0)
            from LedgerEntry e where e.accountId = :accountId
            """)
    BigDecimal netMovement(@Param("accountId") Long accountId);
}
