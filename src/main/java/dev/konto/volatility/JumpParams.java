package dev.konto.volatility;

/**
 * Optional jump in the state equation: each day, with probability {@code prob}, h gets an extra
 * N(mean, std^2). Makes a sudden large rise in volatility plausible under the prior.
 */
public record JumpParams(double prob, double mean, double std) {

    /** The jump variant fitted in Undertow (docs/results/fit.json). */
    public static final JumpParams FITTED = new JumpParams(0.003, 1.5, 0.5);

    public JumpParams {
        if (prob < 0 || prob > 1 || std < 0) {
            throw new IllegalArgumentException("Need 0 <= prob <= 1 and std >= 0");
        }
    }

    /** E[exp(J)] for the jump J. */
    public double expectedExp() {
        return (1.0 - prob) + prob * Math.exp(mean + 0.5 * std * std);
    }
}
