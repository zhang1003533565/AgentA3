package com.example.appbackend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class SecondhandCategoryInitializer implements ApplicationRunner {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long count = em.createQuery("SELECT COUNT(c) FROM SecondhandCategory c", Long.class)
                .getSingleResult();
        if (count > 0) {
            return;
        }

        Object[][] categories = {
                {1L, "数码产品", 1},
                {2L, "书籍教材", 2},
                {3L, "服饰鞋包", 3},
                {4L, "生活用品", 4},
                {5L, "其他", 5}
        };

        LocalDateTime now = LocalDateTime.now();
        for (Object[] cat : categories) {
            em.createNativeQuery(
                    "INSERT INTO secondhand_category (id, category_name, sort, create_time) VALUES (?, ?, ?, ?)"
            )
            .setParameter(1, cat[0])
            .setParameter(2, cat[1])
            .setParameter(3, cat[2])
            .setParameter(4, now)
            .executeUpdate();
        }
    }
}
