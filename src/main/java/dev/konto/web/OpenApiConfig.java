package dev.konto.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI kontoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Konto API")
                .version("0.1.0")
                .description("""
                        Multi-currency accounts with an idempotent double-entry ledger, plus a quantitative finance toolkit.

                        Transfers require an `Idempotency-Key` header: repeating a request with the same key returns the \
                        original transfer instead of moving money twice. Money amounts have up to 4 decimals; volatility \
                        and return inputs on the quant endpoints are decimals (0.2 = 20%)."""));
    }
}
