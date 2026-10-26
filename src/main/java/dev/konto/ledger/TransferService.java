package dev.konto.ledger;

import dev.konto.account.Account;
import dev.konto.account.AccountRepository;
import dev.konto.fx.FxService;
import dev.konto.web.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;
    private final FxService fx;

    public TransferService(AccountRepository accounts, TransferRepository transfers,
                           LedgerEntryRepository ledger, FxService fx) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.fx = fx;
    }

    /**
     * Atomically moves {@code amount} (in the source currency) between two accounts, converting at the
     * current FX rate when the currencies differ. Replaying the same idempotency key returns the original
     * transfer instead of moving money twice.
     */
    @Transactional
    public Transfer transfer(String idempotencyKey, Long fromId, Long toId, BigDecimal requested) {
        BigDecimal amount = requested.setScale(4, RoundingMode.HALF_EVEN);
        if (amount.signum() <= 0) {
            throw DomainException.invalid("Amount must be positive");
        }
        if (fromId.equals(toId)) {
            throw DomainException.invalid("Cannot transfer to the same account");
        }

        Optional<Transfer> replay = transfers.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return matchingReplay(replay.get(), fromId, toId, amount);
        }

        List<Account> locked = accounts.lockAllById(List.of(fromId, toId));
        Account from = find(locked, fromId);
        Account to = find(locked, toId);

        if (from.getBalance().compareTo(amount) < 0) {
            throw DomainException.insufficientFunds(fromId);
        }

        BigDecimal rate = fx.rate(from.getCurrency(), to.getCurrency());
        BigDecimal credited = fx.convert(amount, from.getCurrency(), to.getCurrency());

        from.debit(amount);
        to.credit(credited);

        Transfer saved = transfers.saveAndFlush(new Transfer(idempotencyKey, fromId, toId, amount, credited, rate));
        ledger.save(new LedgerEntry(fromId, saved.getId(), Direction.DEBIT, amount, from.getBalance()));
        ledger.save(new LedgerEntry(toId, saved.getId(), Direction.CREDIT, credited, to.getBalance()));
        return saved;
    }

    private static Transfer matchingReplay(Transfer existing, Long fromId, Long toId, BigDecimal amount) {
        boolean same = existing.getFromAccountId().equals(fromId)
                && existing.getToAccountId().equals(toId)
                && existing.getAmount().compareTo(amount) == 0;
        if (!same) {
            throw DomainException.conflict("Idempotency key was already used with different parameters");
        }
        return existing;
    }

    private static Account find(List<Account> locked, Long id) {
        return locked.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> DomainException.notFound("Account", id));
    }
}
