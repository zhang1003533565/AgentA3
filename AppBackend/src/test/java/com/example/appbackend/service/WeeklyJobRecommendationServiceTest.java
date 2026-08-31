package com.example.appbackend.service;

import com.example.appbackend.dto.WeeklyJobRecommendationDTO;
import com.example.appbackend.entity.WeeklyJobRecommendation;
import com.example.appbackend.repository.WeeklyJobRecommendationRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyJobRecommendationServiceTest {

    @Test
    void refreshForWeekBuildsBossSearchLinks() {
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                authorization -> List.of(
                        item("Java 微服务后端工程师", "15k-25k", "Java, Spring Boot, MySQL"),
                        item("前端可视化工程师", "12k-20k", "Vue, TypeScript, ECharts")
                )
        );

        List<WeeklyJobRecommendationDTO> result = service.refreshForWeek(LocalDate.of(2026, 8, 4), null);

        assertEquals(2, result.size());
        assertTrue(result.get(0).getRecruitmentLink().contains("query="));
        assertTrue(result.get(0).getRecruitmentLink().contains("zhipin.com"));
        assertEquals("Java 微服务后端工程师", result.get(0).getJobTitle());
        assertEquals(WeeklyJobRecommendationService.SALARY_ON_BOSS_HINT, result.get(0).getSalary());
    }

    @Test
    void listLatestReturnsStoredWeek() {
        FakeWeeklyJobRecommendationRepository repository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendation saved = new WeeklyJobRecommendation();
        saved.setId(1L);
        saved.setWeekStartDate(LocalDate.of(2026, 8, 4));
        saved.setWeekEndDate(LocalDate.of(2026, 8, 10));
        saved.setSortOrder(1);
        saved.setJobTitle("测试开发工程师");
        saved.setSalary("10k-18k");
        saved.setSkills("Python, Pytest");
        saved.setRecruitmentLink("https://example.com");
        saved.setSource("weekly-job-radar-agent");
        repository.rows.add(saved);

        WeeklyJobRecommendationService service = new WeeklyJobRecommendationService(
                repository.proxy(),
                authorization -> List.of()
        );

        List<WeeklyJobRecommendationDTO> result = service.listLatest();

        assertEquals(1, result.size());
        assertEquals("测试开发工程师", result.get(0).getJobTitle());
        assertEquals(WeeklyJobRecommendationService.SALARY_ON_BOSS_HINT, result.get(0).getSalary());
    }

    private WeeklyJobRecommendationClient.GeneratedJobRecommendation item(
            String jobTitle,
            String salary,
            String skills) {
        return new WeeklyJobRecommendationClient.GeneratedJobRecommendation(jobTitle, salary, skills);
    }

    private static class FakeWeeklyJobRecommendationRepository {
        private final List<WeeklyJobRecommendation> rows = new ArrayList<>();
        private long nextId = 1L;

        WeeklyJobRecommendationRepository proxy() {
            return (WeeklyJobRecommendationRepository) Proxy.newProxyInstance(
                    WeeklyJobRecommendationRepository.class.getClassLoader(),
                    new Class<?>[]{WeeklyJobRecommendationRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findLatestWeekStartDate" -> rows.stream()
                                .map(WeeklyJobRecommendation::getWeekStartDate)
                                .max(Comparator.naturalOrder());
                        case "findByWeekStartDateOrderBySortOrderAsc" -> rows.stream()
                                .filter(item -> item.getWeekStartDate().equals(args[0]))
                                .sorted(Comparator.comparing(WeeklyJobRecommendation::getSortOrder))
                                .toList();
                        case "deleteByWeekStartDate" -> {
                            rows.removeIf(item -> item.getWeekStartDate().equals(args[0]));
                            yield null;
                        }
                        case "saveAll" -> {
                            Iterable<WeeklyJobRecommendation> incoming = (Iterable<WeeklyJobRecommendation>) args[0];
                            List<WeeklyJobRecommendation> saved = new ArrayList<>();
                            for (WeeklyJobRecommendation item : incoming) {
                                if (item.getId() == null) {
                                    item.setId(nextId++);
                                }
                                rows.removeIf(existing -> existing.getId() != null && existing.getId().equals(item.getId()));
                                rows.add(item);
                                saved.add(item);
                            }
                            yield saved;
                        }
                        case "toString" -> "FakeWeeklyJobRecommendationRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
