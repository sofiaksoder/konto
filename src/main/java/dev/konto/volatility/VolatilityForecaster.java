package dev.konto.volatility;

import java.util.Arrays;

/** Runs the particle filter on a return series and summarises it as annualised volatility figures. */
public final class VolatilityForecaster {

    public record Forecast(
            int days,
            double latestVolatilityPercent,
            double nextDayForecastPercent,
            double[] latestQuantilesPercent,   // 5/25/50/75/95%
            double effectiveSampleSize,
            double logLikelihood,
            double[] filteredVolatilityPercent) {
    }

    private VolatilityForecaster() {
    }

    /** {@code dailyReturns} are simple or log returns as decimals (0.01 = 1%). */
    public static Forecast forecast(double[] dailyReturns, SvParams params, int particles, long seed,
                                    Double obsDof, JumpParams jump) {
        ParticleFilter.Result r = ParticleFilter.run(dailyReturns, params, particles, seed, 0.5, true, obsDof, jump);
        int last = dailyReturns.length - 1;
        return new Forecast(
                dailyReturns.length,
                SvParams.annualisePercent(r.meanVol()[last]),
                SvParams.annualisePercent(Math.sqrt(r.predVar()[last])),
                Arrays.stream(r.quantiles()[last]).map(SvParams::annualisePercent).toArray(),
                r.ess()[last],
                r.logLik(),
                Arrays.stream(r.meanVol()).map(SvParams::annualisePercent).toArray());
    }
}
