package dev.konto.quant;

import io.swagger.v3.oas.annotations.tags.Tag;
import dev.konto.quant.BlackScholes.Greeks;
import dev.konto.quant.BlackScholes.OptionType;
import dev.konto.quant.FinanceMath.CashFlow;
import dev.konto.quant.LoanCalculator.Schedule;
import dev.konto.volatility.SvParams;
import dev.konto.volatility.VolatilityForecaster;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Quant toolkit", description = "Loan schedules, XIRR, Black-Scholes, implied volatility, Value-at-Risk")
@RestController
@RequestMapping("/api/quant")
public class QuantController {

    public record LoanRequest(@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal principal,
                              @NotNull @DecimalMin("0") BigDecimal annualRatePercent,
                              @Min(1) @Max(1200) int months) {
    }

    public record CashFlowDto(@NotNull LocalDate date, double amount) {
    }

    public record XirrRequest(@NotEmpty List<@Valid CashFlowDto> cashFlows) {
    }

    public record XirrResponse(double annualisedReturn) {
    }

    public record OptionRequest(@NotNull OptionType type, @Positive double spot, @Positive double strike,
                                @Positive double years, double rate, @Positive double volatility) {
    }

    public record ImpliedVolRequest(@NotNull OptionType type, @Positive double marketPrice, @Positive double spot,
                                    @Positive double strike, @Positive double years, double rate) {
    }

    public record ImpliedVolResponse(double impliedVolatility) {
    }

    /**
     * Give either {@code annualVolatility} (decimal, 0.2 = 20%) or {@code dailyReturns} (decimals, at least 20).
     * With returns, volatility is the particle filter's next-day forecast, annualised.
     */
    public record VarRequest(@Positive double portfolioValue, double annualDrift, Double annualVolatility,
                             @Size(min = 20, max = 20_000) List<Double> dailyReturns,
                             @Min(1) @Max(2520) int horizonDays,
                             @DecimalMin("0.5") @DecimalMax("0.9999") double confidence,
                             @Min(1_000) @Max(5_000_000) int paths, Long seed) {
    }

    public record VarResponse(MonteCarloVar.Result result, double annualVolatilityUsed, String volatilitySource) {
    }

    @PostMapping("/loan-schedule")
    public Schedule loanSchedule(@Valid @RequestBody LoanRequest req) {
        return LoanCalculator.amortize(req.principal(), req.annualRatePercent(), req.months());
    }

    @PostMapping("/xirr")
    public XirrResponse xirr(@Valid @RequestBody XirrRequest req) {
        List<CashFlow> flows = req.cashFlows().stream().map(c -> new CashFlow(c.date(), c.amount())).toList();
        return new XirrResponse(FinanceMath.xirr(flows));
    }

    @PostMapping("/black-scholes")
    public Greeks blackScholes(@Valid @RequestBody OptionRequest req) {
        return BlackScholes.price(req.type(), req.spot(), req.strike(), req.years(), req.rate(), req.volatility());
    }

    @PostMapping("/implied-volatility")
    public ImpliedVolResponse impliedVolatility(@Valid @RequestBody ImpliedVolRequest req) {
        return new ImpliedVolResponse(BlackScholes.impliedVolatility(
                req.type(), req.marketPrice(), req.spot(), req.strike(), req.years(), req.rate()));
    }

    @PostMapping("/value-at-risk")
    public VarResponse valueAtRisk(@Valid @RequestBody VarRequest req) {
        if ((req.annualVolatility() == null) == (req.dailyReturns() == null)) {
            throw new IllegalArgumentException("Provide exactly one of annualVolatility or dailyReturns");
        }
        long seed = req.seed() != null ? req.seed() : System.nanoTime();

        double vol;
        String source;
        if (req.annualVolatility() != null) {
            if (req.annualVolatility() <= 0) {
                throw new IllegalArgumentException("annualVolatility must be positive");
            }
            vol = req.annualVolatility();
            source = "supplied";
        } else {
            double[] returns = req.dailyReturns().stream().mapToDouble(Double::doubleValue).toArray();
            var forecast = VolatilityForecaster.forecast(returns, SvParams.FITTED, 2_000, seed, null, null);
            vol = forecast.nextDayForecastPercent() / 100.0;
            source = "particle-filter forecast";
        }
        var result = MonteCarloVar.simulate(req.portfolioValue(), req.annualDrift(), vol,
                req.horizonDays(), req.confidence(), req.paths(), seed);
        return new VarResponse(result, vol, source);
    }
}
