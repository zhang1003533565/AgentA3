package com.example.appbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Configuration
public class DishSchemaMigration {

    private static final String TABLE_NAME = "dish";
    private static final String LEGACY_STALL_COLUMN = "stall_id";

    @Bean
    public ApplicationRunner migrateDishSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!isMySql(jdbcTemplate.getDataSource()) || !columnExists(jdbcTemplate)) {
                return;
            }

            String nullable = jdbcTemplate.queryForObject(
                    """
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                      AND column_name = ?
                    """,
                    String.class,
                    TABLE_NAME,
                    LEGACY_STALL_COLUMN);

            if (!"YES".equalsIgnoreCase(nullable)) {
                jdbcTemplate.execute("ALTER TABLE dish MODIFY COLUMN stall_id BIGINT NULL");
                log.info("Migrated dish.stall_id to nullable for map-place stalls");
            }
        };
    }

    private boolean isMySql(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("mysql");
        }
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                TABLE_NAME,
                LEGACY_STALL_COLUMN);
        return count != null && count > 0;
    }
}
