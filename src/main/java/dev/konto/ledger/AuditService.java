package dev.konto.ledger;

import dev.konto.account.Account;
import dev.konto.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Re-derives every balance from the append-only ledger and flags any account that disagrees. */
@Service
public class AuditService {

    public record Discrepancy(Long accountId, BigDecimal recordedBalance, BigDecimal ledgerBalance) {
    }

    public record IntegrityReport(boolean consistent, int accountsChecked, List<Discrepancy> discrepancies) {
    }

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public AuditService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public IntegrityReport verify() {
        List<Account> all = accounts.findAll();
        List<Discrepancy> bad = all.stream()
                .map(a -> new Discrepancy(a.getId(), a.getBalance(), ledger.netMovement(a.getId())))
                .filter(d -> d.recordedBalance().compareTo(d.ledgerBalance()) != 0)
                .toList();
        return new IntegrityReport(bad.isEmpty(), all.size(), bad);
    }
}
