package dev.konto.quant;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

public final class FinanceMath {

    public record CashFlow(LocalDate date, double amount) {
    }

    private FinanceMath() {
    }

    /** Net present value with flows[0] at t=0, flows[1] at t=1, and so on. */
    public static double npv(double rate, double[] flows) {
        double total = 0;
        for (int t = 0; t < flows.length; t++) {
            total += flows[t] / Math.pow(1 + rate, t);
        }
        return total;
    }

    /** Internal rate of return for evenly spaced flows. */
    public static double irr(double[] flows) {
        requireSignChange(flows);
        return solveRate(r -> npv(r, flows));
    }

    /** Annualised IRR for irregularly dated flows (Excel XIRR convention, actual/365). */
    public static double xirr(List<CashFlow> flows) {
        requireSignChange(flows.stream().mapToDouble(CashFlow::amount).toArray());
        LocalDate origin = flows.stream().map(CashFlow::date).min(Comparator.naturalOrder()).orElseThrow();
        return solveRate(r -> flows.stream()
                .mapToDouble(f -> f.amount() / Math.pow(1 + r, ChronoUnit.DAYS.between(origin, f.date()) / 365.0))
                .sum());
    }

    private static void requireSignChange(double[] flows) {
        boolean pos = false;
        boolean neg = false;
        for (double f : flows) {
            pos |= f > 0;
            neg |= f < 0;
        }
        if (!pos || !neg) {
            throw new IllegalArgumentException("Cash flows need at least one inflow and one outflow");
        }
    }

    /** Bracketed bisection: slower than Newton but cannot diverge on awkward cash-flow shapes. */
    private static double solveRate(DoubleUnaryOperator f) {
        double lo = -0.999999;
        double hi = 1.0;
        double fLo = f.applyAsDouble(lo);
        while (Math.signum(f.applyAsDouble(hi)) == Math.signum(fLo)) {
            hi *= 2;
            if (hi > 1e9) {
                throw new IllegalArgumentException("No rate of return found");
            }
        }
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2;
            double fMid = f.applyAsDouble(mid);
            if (Math.signum(fMid) == Math.signum(fLo)) {
                lo = mid;
                fLo = fMid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }
}
