package dev.konto.volatility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ParticleFilterTest {

    private static final int PARITY_PARTICLES = 50_000;

    // ---- parity with the Python implementation (Undertow tests/data/parity.json) ----

    @Test
    void plainFilterAgreesWithPythonReference() throws IOException {
        assertParity("plain", null, null);
    }

    @Test
    void jumpVariantAgreesWithPythonReference() throws IOException {
        assertParity("jump", null, new JumpParams(0.003, 1.5, 0.5));
    }

    @Test
    void studentTVariantAgreesWithPythonReference() throws IOException {
        assertParity("t", 20.0, null);
    }

    private static void assertParity(String variant, Double dof, JumpParams jump) throws IOException {
        JsonNode fixture;
        try (InputStream in = ParticleFilterTest.class.getResourceAsStream("/undertow-parity.json")) {
            fixture = new ObjectMapper().readTree(in);
        }
        JsonNode p = fixture.get("params");
        SvParams params = new SvParams(p.get("mu").asDouble(), p.get("phi").asDouble(), p.get("sigma_eta").asDouble());
        double[] returns = toArray(fixture.get("returns"));
        JsonNode ref = fixture.get("variants").get(variant);
        double[] refVol = toArray(ref.get("mean_vol"));
        double[] refMedian = toArray(ref.get("q50"));

        var java = ParticleFilter.run(returns, params, PARITY_PARTICLES, 1, 0.5, true, dof, jump);

        double maxRel = 0;
        double sumRel = 0;
        double maxRelMedian = 0;
        for (int t = 0; t < returns.length; t++) {
            double rel = Math.abs(java.meanVol()[t] - refVol[t]) / refVol[t];
            maxRel = Math.max(maxRel, rel);
            sumRel += rel;
            maxRelMedian = Math.max(maxRelMedian, Math.abs(java.quantiles()[t][2] - refMedian[t]) / refMedian[t]);
        }
        System.out.printf("parity[%s] meanVol maxRel=%.4f avgRel=%.4f medianMaxRel=%.4f logLik java=%.2f py=%.2f%n",
                variant, maxRel, sumRel / returns.length, maxRelMedian, java.logLik(), ref.get("loglik").asDouble());

        assertThat(maxRel).as("max relative error in filtered volatility").isLessThan(0.02);
        assertThat(sumRel / returns.length).as("average relative error").isLessThan(0.005);
        assertThat(maxRelMedian).as("max relative error in median volatility").isLessThan(0.02);
        assertThat(java.logLik()).isCloseTo(ref.get("loglik").asDouble(), within(0.5));
    }

    private static double[] toArray(JsonNode array) {
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).asDouble();
        }
        return out;
    }

    // ---- building blocks ----

    @Test
    void weightsNormaliseEvenForHugeLogWeights() {
        Random rnd = new Random(0);
        for (double scale : new double[]{1, 50, 1000}) {
            double[] logw = rnd.doubles(500).map(x -> (x - 0.5) * scale).toArray();
            double[] w = new double[500];
            ParticleFilter.normalise(logw, w);
            assertThat(Arrays.stream(w).sum()).isCloseTo(1.0, within(1e-12));
            assertThat(Arrays.stream(w).min().orElseThrow()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void logNormaliserMatchesDirectSum() {
        double[] w = new double[3];
        double lse = ParticleFilter.normalise(new double[]{Math.log(0.1), Math.log(0.2), Math.log(0.3)}, w);
        assertThat(lse).isCloseTo(Math.log(0.6), within(1e-12));
    }

    @Test
    void systematicResamplingGivesExactCountsWhenNTimesWeightIsIntegral() {
        double[] weights = {0.5, 0.25, 0.25, 0.0};
        double[] ids = {0, 1, 2, 3};
        double[] out = new double[4];
        ParticleFilter.systematicResample(weights, 0.3, ids, out);
        int[] counts = new int[4];
        for (double id : out) {
            counts[(int) id]++;
        }
        assertThat(counts).containsExactly(2, 1, 1, 0);
    }

    @Test
    void weightedQuantilesOfUniformGridAreSensible() {
        int n = 1001;
        double[] values = new double[n];
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            values[i] = 1.0 + i / 1000.0;   // positive, as volatilities are
            weights[i] = 1.0 / n;
        }
        double[] q = ParticleFilter.weightedQuantiles(values, weights);
        assertThat(q[0]).isCloseTo(1.05, within(0.01));
        assertThat(q[2]).isCloseTo(1.50, within(0.01));
        assertThat(q[4]).isCloseTo(1.95, within(0.01));
        for (int i = 1; i < q.length; i++) {
            assertThat(q[i]).isGreaterThan(q[i - 1]);
        }
    }

    @Test
    void studentTDensityIntegratesToOneWithUnitVariance() {
        double sum = 0;
        double variance = 0;
        double dx = 0.01;
        for (double x = -80; x <= 80; x += dx) {
            double d = Math.exp(ParticleFilter.studentTLogPdf(x, 0.0, 5.0));
            sum += d * dx;
            variance += d * x * x * dx;
        }
        assertThat(sum).isCloseTo(1.0, within(1e-3));
        assertThat(variance).isCloseTo(1.0, within(2e-2));
    }

    @Test
    void studentTTendsToGaussianForLargeDof() {
        for (double h : new double[]{-1.0, 0.5}) {
            assertThat(ParticleFilter.studentTLogPdf(0.7, h, 1e6))
                    .isCloseTo(ParticleFilter.gaussianLogPdf(0.7, h), within(1e-4));
        }
    }

    @Test
    void logGammaMatchesKnownValues() {
        assertThat(ParticleFilter.logGamma(1)).isCloseTo(0.0, within(1e-12));
        assertThat(ParticleFilter.logGamma(5)).isCloseTo(Math.log(24), within(1e-12));
        assertThat(ParticleFilter.logGamma(0.5)).isCloseTo(0.5 * Math.log(Math.PI), within(1e-12));
    }

    // ---- behaviour ----

    private static double[] simulate(int n, long seed, SvParams p) {
        Random rnd = new Random(seed);
        double h = p.mu() + p.stationaryStd() * rnd.nextGaussian();
        double[] r = new double[n];
        for (int t = 0; t < n; t++) {
            if (t > 0) {
                h = p.mu() + p.phi() * (h - p.mu()) + p.sigmaEta() * rnd.nextGaussian();
            }
            r[t] = Math.exp(h / 2) * rnd.nextGaussian();
        }
        return r;
    }

    @Test
    void sameSeedGivesIdenticalOutputAndDifferentSeedDoesNot() {
        double[] r = simulate(150, 3, SvParams.DEFAULT);
        var a = ParticleFilter.run(r, SvParams.DEFAULT, 300, 7);
        var b = ParticleFilter.run(r, SvParams.DEFAULT, 300, 7);
        var c = ParticleFilter.run(r, SvParams.DEFAULT, 300, 8);
        assertThat(a.meanVol()).containsExactly(b.meanVol());
        assertThat(a.meanVol()).isNotEqualTo(c.meanVol());
    }

    @Test
    void outputHasSensibleShapeAndOrdering() {
        double[] r = simulate(120, 4, SvParams.DEFAULT);
        var f = ParticleFilter.run(r, SvParams.DEFAULT, 400, 0);
        assertThat(f.meanVol()).hasSize(120);
        for (int t = 0; t < 120; t++) {
            double[] q = f.quantiles()[t];
            for (int i = 1; i < q.length; i++) {
                assertThat(q[i]).isGreaterThanOrEqualTo(q[i - 1]);
            }
            assertThat(f.ess()[t]).isBetween(0.0, 400.0 + 1e-6);
            if (f.resampled()[t]) {
                assertThat(f.ess()[t]).isLessThan(200);
            }
        }
        assertThat(f.logLik()).isCloseTo(Arrays.stream(f.logLikT()).sum(), within(1e-9));
    }

    @Test
    void trueParametersScoreHigherLogLikelihoodThanWrongOnes() {
        double[] r = simulate(600, 5, SvParams.DEFAULT);
        double good = 0;
        double bad = 0;
        for (int s = 0; s < 4; s++) {
            good += ParticleFilter.run(r, SvParams.DEFAULT, 1000, s).logLik();
            bad += ParticleFilter.run(r, new SvParams(-9.2, 0.5, 0.6), 1000, s).logLik();
        }
        assertThat(good).isGreaterThan(bad);
    }

    @Test
    void filterTracksAStepUpInVolatility() {
        double[] r = new double[300];
        Random rnd = new Random(9);
        for (int t = 0; t < r.length; t++) {
            r[t] = (t < 150 ? 0.007 : 0.025) * rnd.nextGaussian();
        }
        var f = ParticleFilter.run(r, SvParams.FITTED, 3000, 1);
        assertThat(f.meanVol()[140]).isBetween(0.004, 0.012);
        assertThat(f.meanVol()[290]).isBetween(0.017, 0.035);
    }

    @Test
    void forecastIsAnnualisedAndConsistentWithTheFilter() {
        double[] r = simulate(200, 11, SvParams.FITTED);
        var fc = VolatilityForecaster.forecast(r, SvParams.FITTED, 2000, 2, null, null);
        var raw = ParticleFilter.run(r, SvParams.FITTED, 2000, 2);
        assertThat(fc.latestVolatilityPercent()).isCloseTo(raw.meanVol()[199] * Math.sqrt(252) * 100, within(1e-9));
        assertThat(fc.filteredVolatilityPercent()).hasSize(200);
        assertThat(fc.latestQuantilesPercent()).hasSize(5);
    }

    @Test
    void invalidArgumentsAreRejected() {
        double[] r = simulate(30, 1, SvParams.DEFAULT);
        assertThatThrownBy(() -> ParticleFilter.run(r, SvParams.DEFAULT, 100, 0, 0.5, true, 2.0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParticleFilter.run(r, SvParams.DEFAULT, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvParams(-9, 1.0, 0.1)).isInstanceOf(IllegalArgumentException.class);
    }
}
