package dev.konto.account;

import io.swagger.v3.oas.annotations.tags.Tag;
import dev.konto.ledger.Direction;
import dev.konto.ledger.LedgerEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Tag(name = "Accounts", description = "Open accounts, read balances and statements")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    public record OpenAccountRequest(
            @NotBlank String owner,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DecimalMin("0") BigDecimal initialDeposit) {
    }

    public record AccountView(Long id, String owner, String currency, BigDecimal balance) {
        static AccountView of(Account a) {
            return new AccountView(a.getId(), a.getOwner(), a.getCurrency(), a.getBalance());
        }
    }

    public record LedgerEntryView(Long id, Long transferId, Long counterpartyAccountId, Direction direction,
                                  BigDecimal amount, BigDecimal balanceAfter, Instant createdAt) {
        static LedgerEntryView of(AccountService.StatementLine line) {
            LedgerEntry e = line.entry();
            return new LedgerEntryView(e.getId(), e.getTransferId(), line.counterpartyAccountId(), e.getDirection(),
                    e.getAmount(), e.getBalanceAfter(), e.getCreatedAt());
        }
    }

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView open(@Valid @RequestBody OpenAccountRequest req) {
        return AccountView.of(service.open(req.owner(), req.currency(), req.initialDeposit()));
    }

    @GetMapping
    public List<AccountView> list() {
        return service.list().stream().map(AccountView::of).toList();
    }

    @GetMapping("/{id}")
    public AccountView get(@PathVariable Long id) {
        return AccountView.of(service.get(id));
    }

    @GetMapping("/{id}/ledger")
    public List<LedgerEntryView> ledger(@PathVariable Long id,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return service.statement(id, page, size).stream().map(LedgerEntryView::of).toList();
    }
}
