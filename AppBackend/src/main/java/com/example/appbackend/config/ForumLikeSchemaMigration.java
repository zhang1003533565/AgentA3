package com.example.appbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class ForumLikeSchemaMigration {

    private static final String TABLE_NAME = "forum_like";
    private static final String INDEX_NAME = "uk_forum_like_user_target";
    private static final String USER_INDEX_NAME = "idx_forum_like_user_id";

    @Bean
    public ApplicationRunner migrateForumLikeSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, TABLE_NAME)) {
                return;
            }

            dropPostForeignKeys(jdbcTemplate);
            rebuildUniqueIndex(jdbcTemplate);
        };
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private void dropPostForeignKeys(JdbcTemplate jdbcTemplate) {
        List<String> constraints = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT constraint_name
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND referenced_table_name = 'forum_post'
                """,
                String.class,
                TABLE_NAME);

        for (String constraint : constraints) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " DROP FOREIGN KEY `" + constraint + "`");
            log.info("Dropped legacy forum_like foreign key: {}", constraint);
        }
    }

    private void rebuildUniqueIndex(JdbcTemplate jdbcTemplate) {
        ensureUserIndex(jdbcTemplate);
        List<Map<String, Object>> indexRows = jdbcTemplate.queryForList(
                """
                SELECT seq_in_index AS Seq_in_index, column_name AS Column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """,
                TABLE_NAME,
                INDEX_NAME);
        if (isCurrentUniqueIndex(indexRows)) {
            return;
        }
        if (!indexRows.isEmpty()) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " DROP INDEX `" + INDEX_NAME + "`");
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD UNIQUE KEY `" + INDEX_NAME + "` (user_id, target_id, target_type)");
    }

    private void ensureUserIndex(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                TABLE_NAME,
                USER_INDEX_NAME);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD INDEX `" + USER_INDEX_NAME + "` (user_id)");
        }
    }

    private boolean isCurrentUniqueIndex(List<Map<String, Object>> indexRows) {
        if (indexRows.size() != 3) {
            return false;
        }
        List<String> columns = indexRows.stream()
                .sorted((left, right) -> Integer.compare(
                        ((Number) left.get("Seq_in_index")).intValue(),
                        ((Number) right.get("Seq_in_index")).intValue()))
                .map(row -> String.valueOf(row.get("Column_name")))
                .toList();
        return columns.equals(List.of("user_id", "target_id", "target_type"));
    }
}
