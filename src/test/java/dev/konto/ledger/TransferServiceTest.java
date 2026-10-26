package dev.konto.ledger;

import dev.konto.MariaDbIntegrationTest;
import dev.konto.account.AccountRepository;
import dev.konto.account.AccountService;
import dev.konto.web.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransferServiceTest extends MariaDbIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired AccountRepository accounts;
    @Autowired TransferService transfers;
    @Autowired AuditService audit;

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void crossCurrencyTransferConvertsAndKeepsLedgerBalanced() {
        var usd = accountService.open("alice", "USD", bd("1000"));
        var eur = accountService.open("bob", "EUR", bd("0"));

        Transfer t = transfers.transfer(key(), usd.getId(), eur.getId(), bd("100"));

        assertThat(t.getCreditedAmount()).isEqualByComparingTo("92.0000");
        assertThat(accounts.findById(usd.getId()).orElseThrow().getBalance()).isEqualByComparingTo("900");
        assertThat(accounts.findById(eur.getId()).orElseThrow().getBalance()).isEqualByComparingTo("92");
        assertThat(audit.verify().consistent()).isTrue();
    }

    @Test
    void replayingAnIdempotencyKeyDoesNotMoveMoneyTwice() {
        var a = accountService.open("a", "USD", bd("500"));
        var b = accountService.open("b", "USD", bd("0"));
        String key = key();

        Transfer first = transfers.transfer(key, a.getId(), b.getId(), bd("200"));
        Transfer second = transfers.transfer(key, a.getId(), b.getId(), bd("200"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(accounts.findById(a.getId()).orElseThrow().getBalance()).isEqualByComparingTo("300");
        assertThatThrownBy(() -> transfers.transfer(key, a.getId(), b.getId(), bd("1")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void overdraftIsRejected() {
        var a = accountService.open("a", "USD", bd("10"));
        var b = accountService.open("b", "USD", bd("0"));

        assertThatThrownBy(() -> transfers.transfer(key(), a.getId(), b.getId(), bd("10.0001")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void concurrentOpposingTransfersConserveMoneyWithoutDeadlock() throws Exception {
        var a = accountService.open("a", "USD", bd("1000"));
        var b = accountService.open("b", "USD", bd("1000"));

        List<Callable<Transfer>> jobs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            boolean forward = i % 2 == 0;
            Long from = forward ? a.getId() : b.getId();
            Long to = forward ? b.getId() : a.getId();
            jobs.add(() -> transfers.transfer(key(), from, to, bd("1")));
        }
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Transfer> f : pool.invokeAll(jobs)) {
                f.get();
            }
        }

        BigDecimal total = accounts.findById(a.getId()).orElseThrow().getBalance()
                .add(accounts.findById(b.getId()).orElseThrow().getBalance());
        assertThat(total).isEqualByComparingTo("2000");
        assertThat(audit.verify().consistent()).isTrue();
    }
}
