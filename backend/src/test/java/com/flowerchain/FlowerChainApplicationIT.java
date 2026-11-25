package com.flowerchain;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Smoke test: the full application context starts against a real PostgreSQL 16 database.
 *
 * <p>A successful start proves that every Flyway migration applies cleanly and that Hibernate
 * ({@code ddl-auto=validate}) accepts every JPA entity against the migrated schema. Requires Docker,
 * so it runs in CI only (DEC-015); run locally with {@code -DskipITs} to skip it.
 */
@SpringBootTest
@Testcontainers
class FlowerChainApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Test
    void contextStartsWithAllMigrationsAppliedAndSchemaValidated() {
        assertThat(applicationContext).isNotNull();

        MigrationInfo[] migrations = flyway.info().all();
        assertThat(migrations).as("Flyway migrations on the classpath").isNotEmpty();
        assertThat(migrations)
                .as("every Flyway migration is applied successfully")
                .allSatisfy(m -> assertThat(m.getState())
                        .as("state of migration %s (%s)", m.getVersion(), m.getDescription())
                        .isEqualTo(MigrationState.SUCCESS));
        assertThat(flyway.info().pending()).as("pending Flyway migrations").isEmpty();

        // Guards the meaning of this test: the context only proves entity/schema agreement if
        // Hibernate actually validated the schema rather than skipping or generating it.
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }
}
