package dev.konto.fx;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "FX", description = "Exchange-rate estimates")
@RestController
@RequestMapping("/api/fx")
public class FxController {

    public record RateView(String from, String to, BigDecimal rate) {
    }

    private final FxService fx;

    public FxController(FxService fx) {
        this.fx = fx;
    }

    /** Estimate used by the send-money preview; the transfer itself records the rate actually applied. */
    @GetMapping("/rate")
    public RateView rate(@RequestParam String from, @RequestParam String to) {
        return new RateView(from, to, fx.rate(from, to));
    }
}
