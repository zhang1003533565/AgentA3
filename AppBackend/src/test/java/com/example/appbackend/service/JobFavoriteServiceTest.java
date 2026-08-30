package com.example.appbackend.service;

import com.example.appbackend.dto.WeeklyJobRecommendationDTO;
import com.example.appbackend.entity.JobFavorite;
import com.example.appbackend.entity.WeeklyJobRecommendation;
import com.example.appbackend.repository.JobFavoriteRepository;
import com.example.appbackend.repository.WeeklyJobRecommendationRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobFavoriteServiceTest {

    @Test
    void listFavoriteJobsReturnsStoredRecommendations() {
        FakeJobFavoriteRepository favoriteRepository = new FakeJobFavoriteRepository();
        FakeWeeklyJobRecommendationRepository jobRepository = new FakeWeeklyJobRecommendationRepository();
        WeeklyJobRecommendationService recommendationService = new WeeklyJobRecommendationService(
                jobRepository.proxy(),
                authorization -> List.of()
        );
        JobFavoriteService service = new JobFavoriteService(
                favoriteRepository.proxy(),
                jobRepository.proxy(),
                recommendationService
        );

        WeeklyJobRecommendation job = new WeeklyJobRecommendation();
        job.setId(10L);
        job.setWeekStartDate(LocalDate.of(2026, 8, 4));
        job.setWeekEndDate(LocalDate.of(2026, 8, 10));
        job.setSortOrder(1);
        job.setJobTitle("Java 微服务后端工程师");
        job.setSalary("15k-25k");
        job.setSkills("Java, Spring Boot");
        job.setRecruitmentLink("https://example.com");
        job.setSource("weekly-job-radar-agent");
        jobRepository.rows.add(job);

        JobFavorite favorite = new JobFavorite();
        favorite.setUserId(1L);
        favorite.setRecommendationId(10L);
        favoriteRepository.rows.add(favorite);

        List<WeeklyJobRecommendationDTO> result = service.listFavoriteJobs(1L);

        assertEquals(1, result.size());
        assertEquals("Java 微服务后端工程师", result.get(0).getJobTitle());
        assertTrue(result.get(0).getRecruitmentLink().contains("zhipin.com"));
    }

    @Test
    void getFavoriteRecommendationIdsReturnsDistinctIds() {
        FakeJobFavoriteRepository favoriteRepository = new FakeJobFavoriteRepository();
        JobFavoriteService service = new JobFavoriteService(
                favoriteRepository.proxy(),
                null,
                null
        );

        JobFavorite first = new JobFavorite();
        first.setUserId(2L);
        first.setRecommendationId(3L);
        JobFavorite second = new JobFavorite();
        second.setUserId(2L);
        second.setRecommendationId(8L);
        favoriteRepository.rows.add(first);
        favoriteRepository.rows.add(second);

        Set<Long> ids = service.getFavoriteRecommendationIds(2L);

        assertEquals(Set.of(3L, 8L), ids);
    }

    private static class FakeJobFavoriteRepository {
        private final List<JobFavorite> rows = new ArrayList<>();

        JobFavoriteRepository proxy() {
            return (JobFavoriteRepository) Proxy.newProxyInstance(
                    JobFavoriteRepository.class.getClassLoader(),
                    new Class<?>[]{JobFavoriteRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByUserId" -> rows.stream()
                                .filter(item -> item.getUserId().equals(args[0]))
                                .toList();
                        case "existsByUserIdAndRecommendationId" -> rows.stream()
                                .anyMatch(item -> item.getUserId().equals(args[0])
                                        && item.getRecommendationId().equals(args[1]));
                        case "findByUserIdAndRecommendationId" -> rows.stream()
                                .filter(item -> item.getUserId().equals(args[0])
                                        && item.getRecommendationId().equals(args[1]))
                                .findFirst();
                        case "deleteByUserIdAndRecommendationId" -> {
                            rows.removeIf(item -> item.getUserId().equals(args[0])
                                    && item.getRecommendationId().equals(args[1]));
                            yield null;
                        }
                        case "save" -> {
                            JobFavorite item = (JobFavorite) args[0];
                            rows.add(item);
                            yield item;
                        }
                        case "toString" -> "FakeJobFavoriteRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static class FakeWeeklyJobRecommendationRepository {
        private final List<WeeklyJobRecommendation> rows = new ArrayList<>();

        WeeklyJobRecommendationRepository proxy() {
            return (WeeklyJobRecommendationRepository) Proxy.newProxyInstance(
                    WeeklyJobRecommendationRepository.class.getClassLoader(),
                    new Class<?>[]{WeeklyJobRecommendationRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findAllById" -> {
                            Iterable<Long> ids = (Iterable<Long>) args[0];
                            List<WeeklyJobRecommendation> matched = new ArrayList<>();
                            for (Long id : ids) {
                                rows.stream()
                                        .filter(item -> id.equals(item.getId()))
                                        .findFirst()
                                        .ifPresent(matched::add);
                            }
                            yield matched;
                        }
                        case "toString" -> "FakeWeeklyJobRecommendationRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
