package com.example.appbackend.config;

import com.example.appbackend.repository.PythonProblemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Python 题库种子数据初始化：
 * 仅当 python_problem 表为空时，从 classpath 的 python_problem_seed.json 导入内置题目。
 * 使用原生 INSERT 保留种子文件中的显式 id，与小程序用户本地做题进度（以题目 id 为键）保持兼容。
 */
@Component
public class PythonProblemSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PythonProblemSeeder.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String INSERT_SQL =
            "INSERT INTO python_problem (id, number, title, difficulty, pass_rate, submissions, tags, "
                    + "description, examples, default_code, func_name, testcases, similar_ids, enabled, "
                    + "create_time, update_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Autowired
    private PythonProblemRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            if (repository.count() > 0) {
                return;
            }
            ClassPathResource resource = new ClassPathResource("python_problem_seed.json");
            if (!resource.exists()) {
                log.warn("未找到 Python 题库种子文件 python_problem_seed.json，跳过初始化");
                return;
            }
            List<SeedProblem> seeds;
            try (InputStream in = resource.getInputStream()) {
                seeds = JSON.readValue(in, new TypeReference<List<SeedProblem>>() {});
            }
            LocalDateTime now = LocalDateTime.now();
            for (SeedProblem s : seeds) {
                jdbcTemplate.update(INSERT_SQL,
                        s.id,
                        s.number,
                        s.title,
                        s.difficulty,
                        s.passRate,
                        s.submissions,
                        JSON.writeValueAsString(s.tags),
                        s.description,
                        JSON.writeValueAsString(s.examples),
                        s.defaultCode,
                        s.funcName == null ? "" : s.funcName,
                        JSON.writeValueAsString(s.testcases),
                        JSON.writeValueAsString(s.similarIds),
                        s.enabled == null ? Boolean.TRUE : s.enabled,
                        now,
                        now);
            }
            log.info("Python 题库种子数据初始化完成，共 {} 道题", seeds.size());
        } catch (Exception e) {
            // 种子初始化失败不阻断应用启动，可由管理端手工补录
            log.error("Python 题库种子数据初始化失败", e);
        }
    }

    /** 种子文件结构（字段与小程序 problems.js 历史格式一致） */
    public static class SeedProblem {
        public Long id;
        public Integer number;
        public String title;
        public String difficulty;
        public Double passRate;
        public String submissions;
        public List<String> tags;
        public String description;
        public List<Map<String, Object>> examples;
        public String defaultCode;
        public String funcName;
        public List<Map<String, Object>> testcases;
        public List<Long> similarIds;
        public Boolean enabled;
    }
}
