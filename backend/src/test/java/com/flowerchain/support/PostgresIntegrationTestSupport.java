package com.flowerchain.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests against a real PostgreSQL 16 (never H2, CLAUDE.md §9). Requires Docker, so these
 * tests run in GitHub Actions CI only (DEC-015); locally use {@code -DskipITs}.
 *
 * <p>One container is started per JVM and shared by every subclass, so Spring caches a single application context
 * for all of them. Tests therefore never assume an empty database: each test creates its own branches, products and
 * users with unique codes through {@link TestData} and only asserts on rows it created.
 */
@SpringBootTest
@Import(TestData.class)
public abstract class PostgresIntegrationTestSupport {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestData data;

    @AfterEach
    void clearSecurityContext() {
        TestPrincipals.clear();
    }
}
