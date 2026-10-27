package dev.konto.volatility;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Bootstrap particle filter for the stochastic volatility model, ported from Undertow (pfvol/filter.py).
 *
 * <p>Each day: propagate particles through the state equation, weight them by the likelihood of the
 * return, then resample if the effective sample size falls below {@code essThreshold * N}. Weights
 * are kept in log space. Optional robust variants: a jump component in the state equation, and
 * Student-t observation noise (unit variance) instead of Gaussian.
 */
public final class ParticleFilter {

    public static final double[] QUANTILES = {0.05, 0.25, 0.50, 0.75, 0.95};
    public static final int MAX_PARTICLES = 1_000_000;

    private static final double LOG_2PI = Math.log(2.0 * Math.PI);
    private static final int INDEX_BITS = 20;

    public record Result(
            double[] meanVol,       // E[exp(h_t/2) | r_1:t], daily
            double[][] quantiles,   // [T][5] daily vol at 5/25/50/75/95%, NaN rows if not computed
            double[] ess,           // effective sample size after weighting, before resampling
            boolean[] resampled,
            double[] predVar,       // forecast of next-day variance given r_1:t
            double[] priorMean,     // mean daily vol after predict, before weighting
            double[] logLikT,       // log p(r_t | r_1:t-1)
            double logLik) {
    }

    private ParticleFilter() {
    }

    public static Result run(double[] returns, SvParams params, int particles, long seed) {
        return run(returns, params, particles, seed, 0.5, true, null, null);
    }

    public static Result run(double[] returns, SvParams params, int particles, long seed, double essThreshold,
                             boolean computeQuantiles, Double obsDof, JumpParams jump) {
        if (obsDof != null && obsDof <= 2) {
            throw new IllegalArgumentException("obsDof must be above 2 (unit-variance Student-t)");
        }
        if (particles < 2 || particles > MAX_PARTICLES) {
            throw new IllegalArgumentException("particles must be between 2 and " + MAX_PARTICLES);
        }
        if (returns.length == 0) {
            throw new IllegalArgumentException("returns must not be empty");
        }

        RandomGenerator rng = new SplittableRandom(seed);   // java.base only, works in slim JRE images
        int days = returns.length;
        int n = particles;
        double mu = params.mu();
        double phi = params.phi();
        double sig = params.sigmaEta();
        double jumpFactor = jump == null ? 1.0 : jump.expectedExp();

        double[] meanVol = new double[days];
        double[][] quantiles = new double[days][QUANTILES.length];
        double[] ess = new double[days];
        boolean[] resampled = new boolean[days];
        double[] predVar = new double[days];
        double[] priorMean = new double[days];
        double[] logLikT = new double[days];

        double[] h = new double[n];
        double[] logw = new double[n];
        double[] w = new double[n];
        double[] vol = new double[n];
        double[] next = new double[n];
        double tConstant = obsDof == null ? 0 : studentTConstant(obsDof);
        double stationary = params.stationaryStd();
        for (int i = 0; i < n; i++) {
            h[i] = mu + stationary * rng.nextGaussian();
        }
        Arrays.fill(logw, -Math.log(n));

        for (int t = 0; t < days; t++) {
            if (t > 0) {
                for (int i = 0; i < n; i++) {
                    h[i] = mu + phi * (h[i] - mu) + sig * rng.nextGaussian();
                    if (jump != null && rng.nextDouble() < jump.prob()) {
                        h[i] += jump.mean() + jump.std() * rng.nextGaussian();
                    }
                }
            }
            double prior = 0;
            for (int i = 0; i < n; i++) {
                prior += Math.exp(logw[i]) * Math.exp(h[i] / 2.0);
            }
            priorMean[t] = prior;

            double r = returns[t];
            for (int i = 0; i < n; i++) {
                logw[i] += obsDof == null ? gaussianLogPdf(r, h[i]) : studentTLogPdf(r, h[i], obsDof, tConstant);
            }
            logLikT[t] = normalise(logw, w);
            for (int i = 0; i < n; i++) {
                logw[i] = Math.log(w[i]);
            }

            double mean = 0;
            double sumSq = 0;
            double pv = 0;
            for (int i = 0; i < n; i++) {
                vol[i] = Math.exp(h[i] / 2.0);
                mean += w[i] * vol[i];
                sumSq += w[i] * w[i];
                pv += w[i] * Math.exp(mu + phi * (h[i] - mu) + 0.5 * sig * sig);
            }
            meanVol[t] = mean;
            ess[t] = 1.0 / sumSq;
            predVar[t] = jumpFactor * pv;
            if (computeQuantiles) {
                quantiles[t] = weightedQuantiles(vol, w);
            } else {
                Arrays.fill(quantiles[t], Double.NaN);
            }

            if (ess[t] < essThreshold * n) {
                systematicResample(w, rng.nextDouble(), h, next);
                System.arraycopy(next, 0, h, 0, n);
                Arrays.fill(logw, -Math.log(n));
                resampled[t] = true;
            }
        }
        return new Result(meanVol, quantiles, ess, resampled, predVar, priorMean, logLikT,
                Arrays.stream(logLikT).sum());
    }

    /** Writes normalised weights into {@code out} and returns log(sum(exp(logw))). */
    static double normalise(double[] logw, double[] out) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : logw) {
            max = Math.max(max, v);
        }
        if (!Double.isFinite(max)) {
            throw new ArithmeticException("all particle weights are zero");
        }
        double total = 0;
        for (int i = 0; i < logw.length; i++) {
            out[i] = Math.exp(logw[i] - max);
            total += out[i];
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= total;
        }
        return max + Math.log(total);
    }

    /** Copies {@code source[parent(i)]} into {@code out[i]} using systematic resampling. */
    static void systematicResample(double[] weights, double u, double[] source, double[] out) {
        int n = weights.length;
        double cdf = weights[0];
        int j = 0;
        for (int i = 0; i < n; i++) {
            double position = (u + i) / n;
            // last cdf bucket is treated as 1.0 to guard against rounding
            while (j < n - 1 && cdf < position) {
                j++;
                cdf += weights[j];
            }
            out[i] = source[j];
        }
    }

    /**
     * Weighted quantiles with linear interpolation on the midpoint-adjusted CDF (matches
     * {@code np.interp} in the Python version, clamping outside the first and last midpoint).
     *
     * <p>Sorts by packing the particle index into the low mantissa bits of each (positive) value, so the
     * sort is a primitive long sort. This can only swap values that differ by under 1e-9 relative.
     */
    static double[] weightedQuantiles(double[] values, double[] weights) {
        int n = values.length;
        long[] keys = new long[n];
        long mask = (1L << INDEX_BITS) - 1;
        for (int i = 0; i < n; i++) {
            keys[i] = (Double.doubleToLongBits(values[i]) & ~mask) | i;
        }
        Arrays.sort(keys);

        double[] sortedValues = new double[n];
        double[] cdf = new double[n];
        double running = 0;
        for (int k = 0; k < n; k++) {
            int idx = (int) (keys[k] & mask);
            sortedValues[k] = values[idx];
            running += weights[idx];
            cdf[k] = running - 0.5 * weights[idx];
        }

        double[] out = new double[QUANTILES.length];
        int k = 0;
        for (int qi = 0; qi < QUANTILES.length; qi++) {
            double q = QUANTILES[qi];
            if (q <= cdf[0]) {
                out[qi] = sortedValues[0];
            } else if (q >= cdf[n - 1]) {
                out[qi] = sortedValues[n - 1];
            } else {
                while (cdf[k + 1] < q) {
                    k++;
                }
                double frac = (q - cdf[k]) / (cdf[k + 1] - cdf[k]);
                out[qi] = sortedValues[k] + frac * (sortedValues[k + 1] - sortedValues[k]);
            }
        }
        return out;
    }

    /** log N(x; 0, exp(h)). */
    static double gaussianLogPdf(double x, double h) {
        return -0.5 * (h + x * x * Math.exp(-h) + LOG_2PI);
    }

    /** log density of x = exp(h/2) * e where e is Student-t with {@code dof} degrees of freedom, unit variance. */
    static double studentTLogPdf(double x, double h, double dof) {
        return studentTLogPdf(x, h, dof, studentTConstant(dof));
    }

    static double studentTConstant(double dof) {
        return logGamma((dof + 1) / 2) - logGamma(dof / 2) - 0.5 * Math.log(Math.PI * (dof - 2));
    }

    private static double studentTLogPdf(double x, double h, double dof, double constant) {
        return constant - 0.5 * h - 0.5 * (dof + 1) * Math.log1p(x * x * Math.exp(-h) / (dof - 2));
    }

    /** Lanczos approximation (g=7, n=9), accurate to about 1e-15 for positive arguments. */
    static double logGamma(double x) {
        double[] coef = {0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
                1.5056327351493116e-7};
        if (x < 0.5) {
            return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - logGamma(1 - x);
        }
        x -= 1;
        double a = coef[0];
        double t = x + 7.5;
        for (int i = 1; i < 9; i++) {
            a += coef[i] / (x + i);
        }
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }
}
