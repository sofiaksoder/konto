package dev.konto.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Long fromAccountId;

    @Column(nullable = false, updatable = false)
    private Long toAccountId;

    /** Amount debited, in the source account's currency. */
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Amount credited, in the destination account's currency. */
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal creditedAmount;

    @Column(nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal fxRate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Transfer() {
    }

    public Transfer(String idempotencyKey, Long fromAccountId, Long toAccountId,
                    BigDecimal amount, BigDecimal creditedAmount, BigDecimal fxRate) {
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.creditedAmount = creditedAmount;
        this.fxRate = fxRate;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getCreditedAmount() {
        return creditedAmount;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
