package dev.konto.quant;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class LoanCalculator {

    public record Installment(int number, BigDecimal payment, BigDecimal interest, BigDecimal principal,
                              BigDecimal balance) {
    }

    public record Schedule(BigDecimal monthlyPayment, BigDecimal totalInterest, BigDecimal totalPaid,
                           List<Installment> installments) {
    }

    private LoanCalculator() {
    }

    /** Fixed-rate amortization; the final instalment absorbs rounding so the balance ends at exactly zero. */
    public static Schedule amortize(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (principal.signum() <= 0 || months <= 0 || annualRatePercent.signum() < 0) {
            throw new IllegalArgumentException("principal and months must be positive, rate non-negative");
        }
        MathContext mc = MathContext.DECIMAL128;
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), mc);

        BigDecimal payment;
        if (monthlyRate.signum() == 0) {
            payment = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_EVEN);
        } else {
            BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(months, mc);
            payment = principal.multiply(monthlyRate).multiply(growth)
                    .divide(growth.subtract(BigDecimal.ONE), mc)
                    .setScale(2, RoundingMode.HALF_EVEN);
        }

        List<Installment> rows = new ArrayList<>(months);
        BigDecimal balance = principal.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (int i = 1; i <= months; i++) {
            BigDecimal interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal principalPart = (i == months) ? balance : payment.subtract(interest).min(balance);
            balance = balance.subtract(principalPart);
            totalInterest = totalInterest.add(interest);
            rows.add(new Installment(i, principalPart.add(interest), interest, principalPart, balance));
        }
        BigDecimal totalPaid = principal.setScale(2, RoundingMode.HALF_EVEN).add(totalInterest);
        return new Schedule(payment, totalInterest, totalPaid, rows);
    }
}
