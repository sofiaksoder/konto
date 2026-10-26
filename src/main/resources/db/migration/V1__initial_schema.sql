-- Initial schema: accounts, transfers and the append-only ledger.
-- Column names and types mirror the JPA entities; Hibernate runs with ddl-auto=validate and
-- refuses to start if this file and the entities drift apart.

CREATE TABLE accounts (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    owner      VARCHAR(255)   NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    balance    DECIMAL(19, 4) NOT NULL,
    version    BIGINT         NOT NULL,
    created_at DATETIME(6)    NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE transfers (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    idempotency_key  VARCHAR(100)   NOT NULL,
    from_account_id  BIGINT         NOT NULL,
    to_account_id    BIGINT         NOT NULL,
    amount           DECIMAL(19, 4) NOT NULL,
    credited_amount  DECIMAL(19, 4) NOT NULL,
    fx_rate          DECIMAL(19, 8) NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transfers_from FOREIGN KEY (from_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transfers_to   FOREIGN KEY (to_account_id)   REFERENCES accounts (id)
) ENGINE = InnoDB;

CREATE TABLE ledger_entries (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    account_id    BIGINT         NOT NULL,
    transfer_id   BIGINT         NULL,           -- null for opening deposits
    direction     VARCHAR(6)     NOT NULL,       -- DEBIT or CREDIT
    amount        DECIMAL(19, 4) NOT NULL,
    balance_after DECIMAL(19, 4) NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT fk_ledger_account  FOREIGN KEY (account_id)  REFERENCES accounts (id),
    CONSTRAINT fk_ledger_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id)
) ENGINE = InnoDB;

CREATE INDEX idx_ledger_account ON ledger_entries (account_id, id);
