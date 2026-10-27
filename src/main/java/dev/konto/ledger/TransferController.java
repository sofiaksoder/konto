package dev.konto.ledger;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@Tag(name = "Transfers and audit", description = "Move money between accounts (idempotent) and verify the ledger")
@RestController
@RequestMapping("/api")
public class TransferController {

    public record TransferRequest(
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount) {
    }

    public record TransferView(Long id, Long fromAccountId, Long toAccountId, BigDecimal amount,
                               BigDecimal creditedAmount, BigDecimal fxRate, Instant createdAt) {
        static TransferView of(Transfer t) {
            return new TransferView(t.getId(), t.getFromAccountId(), t.getToAccountId(), t.getAmount(),
                    t.getCreditedAmount(), t.getFxRate(), t.getCreatedAt());
        }
    }

    private final TransferService transfers;
    private final AuditService audit;

    public TransferController(TransferService transfers, AuditService audit) {
        this.transfers = transfers;
        this.audit = audit;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferView transfer(@RequestHeader("Idempotency-Key") String key,
                                 @Valid @RequestBody TransferRequest req) {
        return TransferView.of(transfers.transfer(key, req.fromAccountId(), req.toAccountId(), req.amount()));
    }

    @GetMapping("/audit/integrity")
    public AuditService.IntegrityReport integrity() {
        return audit.verify();
    }
}
