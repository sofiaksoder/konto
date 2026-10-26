package dev.konto.account;

import dev.konto.fx.FxService;
import dev.konto.ledger.Direction;
import dev.konto.ledger.LedgerEntry;
import dev.konto.ledger.LedgerEntryRepository;
import dev.konto.ledger.Transfer;
import dev.konto.ledger.TransferRepository;
import dev.konto.web.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferRepository transfers;
    private final FxService fx;

    /** A ledger entry with the account on the other side of its transfer (null for opening deposits). */
    public record StatementLine(LedgerEntry entry, Long counterpartyAccountId) {
    }

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger,
                          TransferRepository transfers, FxService fx) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
        this.fx = fx;
    }

    @Transactional
    public Account open(String owner, String currency, BigDecimal initialDeposit) {
        if (!fx.supports(currency)) {
            throw DomainException.invalid("Unsupported currency " + currency);
        }
        Account account = accounts.save(new Account(owner, currency));
        BigDecimal deposit = initialDeposit.setScale(4, RoundingMode.HALF_EVEN);
        if (deposit.signum() > 0) {
            account.credit(deposit);
            ledger.save(new LedgerEntry(account.getId(), null, Direction.CREDIT, deposit, account.getBalance()));
        }
        return account;
    }

    @Transactional(readOnly = true)
    public Account get(Long id) {
        return accounts.findById(id).orElseThrow(() -> DomainException.notFound("Account", id));
    }

    @Transactional(readOnly = true)
    public List<Account> list() {
        return accounts.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public List<StatementLine> statement(Long accountId, int page, int size) {
        get(accountId);
        List<LedgerEntry> entries = ledger.findByAccountIdOrderByIdDesc(
                accountId, PageRequest.of(page, Math.min(size, 200)));
        Map<Long, Transfer> byId = transfers.findAllById(entries.stream()
                        .map(LedgerEntry::getTransferId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Transfer::getId, Function.identity()));
        return entries.stream().map(e -> {
            Transfer t = e.getTransferId() == null ? null : byId.get(e.getTransferId());
            Long other = t == null ? null
                    : (t.getFromAccountId().equals(accountId) ? t.getToAccountId() : t.getFromAccountId());
            return new StatementLine(e, other);
        }).toList();
    }
}
