package dev.konto.ledger;

import dev.konto.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The schema comes from Flyway migrations, and Hibernate only validates it. */
@SpringBootTest
class MigrationTest extends MariaDbIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto;

    @Test
    void hibernateDoesNotCreateOrAlterTables() {
        assertThat(ddlAuto).isEqualTo("validate");
    }

    @Test
    void everyMigrationAppliedSuccessfully() {
        List<Boolean> results = jdbc.queryForList("select success from flyway_schema_history", Boolean.class);
        assertThat(results).isNotEmpty().doesNotContain(false);
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success order by installed_rank limit 1",
                String.class)).isEqualTo("1");
    }

    @Test
    void foreignKeysAndUniqueIdempotencyKeyAreEnforcedByTheDatabase() {
        Integer constraints = jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = database()
                  and constraint_name in ('uk_transfers_idempotency_key', 'fk_ledger_account', 'fk_transfers_from')
                """, Integer.class);
        assertThat(constraints).isEqualTo(3);
    }
}
