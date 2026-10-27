package dev.konto.volatility;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Volatility", description = "Particle-filter volatility forecasts (ported from Undertow)")
@RestController
@RequestMapping("/api/quant")
public class VolatilityController {

    /**
     * mu, phi and sigmaEta default to the values Undertow fitted on SPY 2010-2018. Set {@code jump} to
     * allow sudden volatility spikes; set {@code dof} (above 2) for fat-tailed return noise.
     */
    public record VolatilityRequest(
            @NotNull @Size(min = 20, max = 20_000) List<Double> returns,
            Double mu, Double phi, Double sigmaEta,
            @Min(100) @Max(200_000) Integer particles,
            Long seed,
            Boolean jump,
            @DecimalMin(value = "2", inclusive = false) Double dof) {
    }

    @PostMapping("/volatility-forecast")
    public VolatilityForecaster.Forecast forecast(@Valid @RequestBody VolatilityRequest req) {
        SvParams p = new SvParams(
                req.mu() != null ? req.mu() : SvParams.FITTED.mu(),
                req.phi() != null ? req.phi() : SvParams.FITTED.phi(),
                req.sigmaEta() != null ? req.sigmaEta() : SvParams.FITTED.sigmaEta());
        return VolatilityForecaster.forecast(
                req.returns().stream().mapToDouble(Double::doubleValue).toArray(),
                p,
                req.particles() != null ? req.particles() : 2_000,
                req.seed() != null ? req.seed() : System.nanoTime(),
                req.dof(),
                Boolean.TRUE.equals(req.jump()) ? JumpParams.FITTED : null);
    }
}
