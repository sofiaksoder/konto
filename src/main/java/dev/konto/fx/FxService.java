package dev.konto.fx;

import dev.konto.web.DomainException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Converts between currencies via a USD pivot. Rates are units of currency per 1 USD;
 * a production system would source these from a market-data feed.
 */
@Service
public class FxService {

    private static final Map<String, BigDecimal> PER_USD = Map.of(
            "USD", new BigDecimal("1"),
            "EUR", new BigDecimal("0.92"),
            "GBP", new BigDecimal("0.79"),
            "SEK", new BigDecimal("10.60"),
            "JPY", new BigDecimal("150.00"),
            "CHF", new BigDecimal("0.88"));

    public boolean supports(String currency) {
        return PER_USD.containsKey(currency);
    }

    /** Rate such that {@code amountInFrom * rate = amountInTo}. */
    public BigDecimal rate(String from, String to) {
        return perUsd(to).divide(perUsd(from), MathContext.DECIMAL64);
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (from.equals(to)) {
            return amount;
        }
        return amount.multiply(rate(from, to)).setScale(4, RoundingMode.HALF_EVEN);
    }

    private BigDecimal perUsd(String currency) {
        BigDecimal r = PER_USD.get(currency);
        if (r == null) {
            throw DomainException.invalid("Unsupported currency " + currency);
        }
        return r;
    }
}
