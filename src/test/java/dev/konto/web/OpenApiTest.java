package dev.konto.web;

import dev.konto.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest extends MariaDbIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void documentListsEveryEndpointGroupAndTheIdempotencyHeader() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Konto API"))
                .andExpect(jsonPath("$.paths['/api/accounts']").exists())
                .andExpect(jsonPath("$.paths['/api/transfers'].post.parameters[?(@.name=='Idempotency-Key')]").exists())
                .andExpect(jsonPath("$.paths['/api/quant/value-at-risk']").exists())
                .andExpect(jsonPath("$.paths['/api/quant/volatility-forecast']").exists())
                .andExpect(jsonPath("$.paths['/api/fx/rate']").exists())
                .andExpect(jsonPath("$.tags[?(@.name=='Transfers and audit')]").exists());
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
