package dev.konto.quant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.random.RandomGenerator;

/**
 * Value-at-Risk and Expected Shortfall under geometric Brownian motion.
 * Paths are split into a fixed number of chunks, each with its own statistically independent generator,
 * and run on virtual threads. The chunking does not depend on core count, so a given seed always
 * produces the same result.
 */
public final class MonteCarloVar {

    private static final int CHUNKS = 16;

    /**
     * {@code histogram} counts simulated losses in {@link #BINS} equal-width bins from {@code lossLow} to
     * {@code lossHigh} (the 0.1% and 99.9% quantiles; outliers fall into the end bins). Negative losses are gains.
     */
    public record Result(double valueAtRisk, double expectedShortfall, double expectedValue,
                         double confidence, int paths, double lossLow, double lossHigh, List<Integer> histogram) {
    }

    public static final int BINS = 41;

    private MonteCarloVar() {
    }

    public static Result simulate(double portfolioValue, double annualDrift, double annualVol, int horizonDays,
                                  double confidence, int paths, long seed) {
        if (portfolioValue <= 0 || annualVol <= 0 || horizonDays <= 0 || paths < CHUNKS
                || confidence <= 0 || confidence >= 1) {
            throw new IllegalArgumentException("Invalid simulation parameters");
        }
        double years = horizonDays / 252.0;
        double drift = (annualDrift - 0.5 * annualVol * annualVol) * years;
        double diffusion = annualVol * Math.sqrt(years);

        // SplittableRandom lives in java.base, so it exists even in slim JRE images without the jdk.random module.
        SplittableRandom root = new SplittableRandom(seed);
        double[] losses = new double[paths];
        int per = paths / CHUNKS;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(CHUNKS);
            for (int c = 0; c < CHUNKS; c++) {
                RandomGenerator rng = root.split();
                int from = c * per;
                int to = (c == CHUNKS - 1) ? paths : from + per;
                futures.add(executor.submit(() -> {
                    for (int i = from; i < to; i++) {
                        double terminal = portfolioValue * Math.exp(drift + diffusion * rng.nextGaussian());
                        losses[i] = portfolioValue - terminal;
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulation interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Simulation failed", e.getCause());
        }

        Arrays.sort(losses);
        int cut = Math.min(paths - 1, (int) Math.ceil(confidence * paths) - 1);
        double tailSum = 0;
        for (int i = cut; i < paths; i++) {
            tailSum += losses[i];
        }
        double expectedShortfall = tailSum / (paths - cut);
        double meanLoss = Arrays.stream(losses).average().orElse(0);
        double low = losses[(int) Math.floor(0.001 * (paths - 1))];
        double high = losses[(int) Math.ceil(0.999 * (paths - 1))];
        int[] counts = new int[BINS];
        double width = (high - low) / BINS;
        for (double loss : losses) {
            int bin = width > 0 ? (int) ((loss - low) / width) : 0;
            counts[Math.max(0, Math.min(BINS - 1, bin))]++;
        }
        List<Integer> histogram = Arrays.stream(counts).boxed().toList();
        return new Result(losses[cut], expectedShortfall, portfolioValue - meanLoss, confidence, paths,
                low, high, histogram);
    }
}
