package dev.konto.quant;

import dev.konto.quant.BlackScholes.OptionType;
import dev.konto.quant.FinanceMath.CashFlow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class QuantTest {

    @Test
    void thirtyYearMortgageMatchesKnownPaymentAndEndsAtZero() {
        var schedule = LoanCalculator.amortize(new BigDecimal("200000"), new BigDecimal("6"), 360);

        assertThat(schedule.monthlyPayment()).isEqualByComparingTo("1199.10");
        assertThat(schedule.installments()).hasSize(360);
        assertThat(schedule.installments().get(359).balance()).isEqualByComparingTo("0");
        BigDecimal principalRepaid = schedule.installments().stream()
                .map(LoanCalculator.Installment::principal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(principalRepaid).isEqualByComparingTo("200000");
    }

    @Test
    void zeroInterestLoanSplitsEvenly() {
        var schedule = LoanCalculator.amortize(new BigDecimal("1200"), BigDecimal.ZERO, 12);
        assertThat(schedule.monthlyPayment()).isEqualByComparingTo("100");
        assertThat(schedule.totalInterest()).isEqualByComparingTo("0");
    }

    @Test
    void irrOfSimpleInvestment() {
        assertThat(FinanceMath.irr(new double[]{-1000, 1100})).isCloseTo(0.10, within(1e-9));
    }

    @Test
    void xirrUsesActualDayCount() {
        var flows = List.of(
                new CashFlow(LocalDate.of(2021, 1, 1), -1000),
                new CashFlow(LocalDate.of(2022, 1, 1), 1100));
        assertThat(FinanceMath.xirr(flows)).isCloseTo(0.10, within(1e-9));
    }

    @Test
    void irrRejectsFlowsWithoutSignChange() {
        assertThatThrownBy(() -> FinanceMath.irr(new double[]{100, 200}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalCdfMatchesReferenceValues() {
        assertThat(BlackScholes.cdf(0)).isCloseTo(0.5, within(1e-12));
        assertThat(BlackScholes.cdf(1.959963984540054)).isCloseTo(0.975, within(1e-9));
        assertThat(BlackScholes.cdf(-8)).isCloseTo(6.22e-16, within(1e-17));
    }

    @Test
    void blackScholesMatchesTextbookValues() {
        var call = BlackScholes.price(OptionType.CALL, 100, 100, 1, 0.05, 0.2);
        var put = BlackScholes.price(OptionType.PUT, 100, 100, 1, 0.05, 0.2);

        assertThat(call.price()).isCloseTo(10.4506, within(1e-4));
        assertThat(put.price()).isCloseTo(5.5735, within(1e-4));
        assertThat(call.delta()).isCloseTo(0.6368, within(1e-4));
        // put-call parity: C - P = S - K e^{-rT}
        assertThat(call.price() - put.price()).isCloseTo(100 - 100 * Math.exp(-0.05), within(1e-9));
    }

    @Test
    void impliedVolatilityRecoversInputVolatility() {
        double price = BlackScholes.price(OptionType.CALL, 100, 110, 0.5, 0.03, 0.27).price();
        assertThat(BlackScholes.impliedVolatility(OptionType.CALL, price, 100, 110, 0.5, 0.03))
                .isCloseTo(0.27, within(1e-8));
    }

    @Test
    void monteCarloVarConvergesToAnalyticLognormalQuantile() {
        double value = 1_000_000, mu = 0.07, sigma = 0.20;
        int days = 10;
        var result = MonteCarloVar.simulate(value, mu, sigma, days, 0.99, 400_000, 42L);

        double t = days / 252.0;
        double z99 = 2.3263478740408408;
        double analytic = value * (1 - Math.exp((mu - 0.5 * sigma * sigma) * t - sigma * Math.sqrt(t) * z99));

        assertThat(result.valueAtRisk()).isCloseTo(analytic, within(analytic * 0.03));
        assertThat(result.expectedShortfall()).isGreaterThan(result.valueAtRisk());
    }

    @Test
    void monteCarloIsDeterministicForAGivenSeed() {
        var a = MonteCarloVar.simulate(1e6, 0.05, 0.2, 5, 0.95, 50_000, 7L);
        var b = MonteCarloVar.simulate(1e6, 0.05, 0.2, 5, 0.95, 50_000, 7L);
        assertThat(a).isEqualTo(b);
    }
}
