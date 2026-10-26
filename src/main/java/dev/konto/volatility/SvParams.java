package dev.konto.volatility;

/**
 * Stochastic volatility model, ported from the Undertow project (pfvol/model.py):
 * <pre>
 *   h_t = mu + phi * (h_{t-1} - mu) + sigmaEta * eta_t
 *   r_t = exp(h_t / 2) * eps_t
 * </pre>
 * {@code h} is the daily log-variance, so daily volatility is {@code exp(h / 2)}.
 */
public record SvParams(double mu, double phi, double sigmaEta) {

    public static final int TRADING_DAYS = 252;

    /** Fitted to SPY returns over 2010-2018 in Undertow (docs/results/fit.json). */
    public static final SvParams FITTED = new SvParams(-9.856218644172229, 0.94, 0.35);

    /** The hand-set defaults in Undertow's {@code SVParams}. */
    public static final SvParams DEFAULT = new SvParams(-9.2, 0.97, 0.15);

    public SvParams {
        if (Math.abs(phi) >= 1 || sigmaEta <= 0) {
            throw new IllegalArgumentException("Need |phi| < 1 and sigmaEta > 0");
        }
    }

    public double stationaryStd() {
        return sigmaEta / Math.sqrt(1.0 - phi * phi);
    }

    /** Daily volatility to annualised percent. */
    public static double annualisePercent(double dailyVol) {
        return dailyVol * Math.sqrt(TRADING_DAYS) * 100.0;
    }
}
