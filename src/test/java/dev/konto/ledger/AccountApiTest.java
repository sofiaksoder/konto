package dev.konto.ledger;

import dev.konto.quant.MonteCarloVar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountApiTest {

    @Autowired MockMvc mvc;

    private long open(String owner, String currency, int deposit) throws Exception {
        MvcResult r = mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"%s\",\"currency\":\"%s\",\"initialDeposit\":%d}".formatted(owner, currency, deposit)))
                .andExpect(status().isCreated()).andReturn();
        String body = r.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void listsAccountsAndShowsCounterpartiesOnTheStatement() throws Exception {
        long a = open("ui-alice", "USD", 500);
        long b = open("ui-bob", "EUR", 0);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("{\"fromAccountId\":%d,\"toAccountId\":%d,\"amount\":100}".formatted(a, b)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/accounts")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==%d)].owner".formatted(a)).value("ui-alice"));

        mvc.perform(get("/api/accounts/%d/ledger".formatted(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$[0].counterpartyAccountId").value((int) b))
                .andExpect(jsonPath("$[1].counterpartyAccountId").doesNotExist());   // opening deposit

        mvc.perform(get("/api/accounts/%d/ledger".formatted(b))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$[0].counterpartyAccountId").value((int) a));
    }

    @Test
    void newAccountsStartWithAFourDecimalZeroBalance() throws Exception {
        long id = open("ui-zero", "SEK", 0);
        mvc.perform(get("/api/accounts/%d".formatted(id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void ratePreviewMatchesTheRateTransfersUse() throws Exception {
        mvc.perform(get("/api/fx/rate?from=USD&to=EUR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(0.92));
        mvc.perform(get("/api/fx/rate?from=USD&to=XXX")).andExpect(status().isBadRequest());
    }

    @Test
    void valueAtRiskReturnsARealHistogramThatCountsEveryPath() {
        var r = MonteCarloVar.simulate(250_000, 0.07, 0.18, 10, 0.95, 100_000, 5L);
        assertThat(r.histogram()).hasSize(MonteCarloVar.BINS);
        assertThat(r.histogram().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100_000);
        assertThat(r.lossLow()).isLessThan(r.valueAtRisk());
        assertThat(r.lossHigh()).isGreaterThan(r.valueAtRisk());
    }

    @Test
    void staticFrontEndIsServed() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }
}
