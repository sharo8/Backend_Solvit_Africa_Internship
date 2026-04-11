package com.solvit.internship_system.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Existing databases may have {@code users.role} as a MySQL ENUM that does not list {@code HR},
 * which causes "Data truncated for column 'role'". This converts the column to VARCHAR once.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class MySqlUsersRoleColumnMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String dataType = jdbcTemplate.query(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'role'",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        return rs.getString(1);
                    }
            );
            if (dataType != null && "enum".equalsIgnoreCase(dataType)) {
                log.info("Migrating users.role from MySQL ENUM to VARCHAR(32) (supports HR and future roles)");
                jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN role VARCHAR(32) NOT NULL");
                log.info("users.role migration completed");
            }
        } catch (Exception e) {
            log.warn("users.role auto-migration skipped: {}", e.getMessage());
        }
    }
}
