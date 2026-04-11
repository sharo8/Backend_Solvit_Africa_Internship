package com.solvit.internship_system.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs {@link Flyway#repair()} before {@link Flyway#migrate()} so a previously failed migration
 * (e.g. V8) does not block startup: repair removes the failed row from {@code flyway_schema_history}
 * and realigns checksums when migration files change in development.
 */
@Configuration
public class FlywayConfig {

    @Value("${app.flyway.repair-before-migrate:true}")
    private boolean repairBeforeMigrate;

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            if (repairBeforeMigrate) {
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
