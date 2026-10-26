package dev.konto.quant;

public final class BlackScholes {

    public enum OptionType {CALL, PUT}

    /** Vega is per 1.00 change in volatility; theta is per year. */
    public record Greeks(double price, double delta, double gamma, double vega, double theta, double rho) {
    }

    private BlackScholes() {
    }

    public static Greeks price(OptionType type, double spot, double strike, double years, double rate, double vol) {
        if (spot <= 0 || strike <= 0 || years <= 0 || vol <= 0) {
            throw new IllegalArgumentException("spot, strike, years and vol must be positive");
        }
        double sqrtT = Math.sqrt(years);
        double d1 = (Math.log(spot / strike) + (rate + vol * vol / 2) * years) / (vol * sqrtT);
        double d2 = d1 - vol * sqrtT;
        double discount = Math.exp(-rate * years);
        double pdf = Math.exp(-d1 * d1 / 2) / Math.sqrt(2 * Math.PI);

        double gamma = pdf / (spot * vol * sqrtT);
        double vega = spot * pdf * sqrtT;
        double decay = -spot * pdf * vol / (2 * sqrtT);

        return switch (type) {
            case CALL -> new Greeks(
                    spot * cdf(d1) - strike * discount * cdf(d2),
                    cdf(d1), gamma, vega,
                    decay - rate * strike * discount * cdf(d2),
                    strike * years * discount * cdf(d2));
            case PUT -> new Greeks(
                    strike * discount * cdf(-d2) - spot * cdf(-d1),
                    cdf(d1) - 1, gamma, vega,
                    decay + rate * strike * discount * cdf(-d2),
                    -strike * years * discount * cdf(-d2));
        };
    }

    /** Solves for the volatility that reproduces a market price. Option price is monotonic in vol, so bisect. */
    public static double impliedVolatility(OptionType type, double marketPrice, double spot, double strike,
                                           double years, double rate) {
        double lo = 1e-6;
        double hi = 5.0;
        if (marketPrice < price(type, spot, strike, years, rate, lo).price()
                || marketPrice > price(type, spot, strike, years, rate, hi).price()) {
            throw new IllegalArgumentException("Market price is outside the range achievable by Black-Scholes");
        }
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2;
            if (price(type, spot, strike, years, rate, mid).price() < marketPrice) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    /** Standard normal CDF, Hart's double-precision rational approximation (error around 1e-15). */
    static double cdf(double x) {
        double abs = Math.abs(x);
        double tail;
        if (abs > 37) {
            tail = 0;
        } else {
            double e = Math.exp(-abs * abs / 2);
            if (abs < 7.07106781186547) {
                double b = 3.52624965998911E-02 * abs + 0.700383064443688;
                b = b * abs + 6.37396220353165;
                b = b * abs + 33.912866078383;
                b = b * abs + 112.079291497871;
                b = b * abs + 221.213596169931;
                b = b * abs + 220.206867912376;
                tail = e * b;
                b = 8.83883476483184E-02 * abs + 1.75566716318264;
                b = b * abs + 16.064177579207;
                b = b * abs + 86.7807322029461;
                b = b * abs + 296.564248779674;
                b = b * abs + 637.333633378831;
                b = b * abs + 793.826512519948;
                b = b * abs + 440.413735824752;
                tail /= b;
            } else {
                double b = abs + 1 / (abs + 2 / (abs + 3 / (abs + 4 / (abs + 0.65))));
                tail = e / b / 2.506628274631;
            }
        }
        return x > 0 ? 1 - tail : tail;
    }
}
