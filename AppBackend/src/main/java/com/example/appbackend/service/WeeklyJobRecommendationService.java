package com.example.appbackend.service;

import com.example.appbackend.dto.WeeklyJobRecommendationDTO;
import com.example.appbackend.entity.WeeklyJobRecommendation;
import com.example.appbackend.repository.WeeklyJobRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeeklyJobRecommendationService {

    private static final int MAX_RECOMMENDATION_COUNT = 5;
    private static final String BOSS_JOB_SEARCH_URL = "https://www.zhipin.com/web/geek/job?query=";
    private static final String SOURCE = "weekly-job-radar-agent";

    private final WeeklyJobRecommendationRepository repository;
    private final WeeklyJobRecommendationClient client;

    public WeeklyJobRecommendationService(
            WeeklyJobRecommendationRepository repository,
            WeeklyJobRecommendationClient client) {
        this.repository = repository;
        this.client = client;
    }

    public List<WeeklyJobRecommendationDTO> listLatest() {
        return repository.findLatestWeekStartDate()
                .map(repository::findByWeekStartDateOrderBySortOrderAsc)
                .map(this::toDtoList)
                .orElseGet(List::of);
    }

    public List<WeeklyJobRecommendationDTO> listLatestOrRefresh(String authorization) {
        List<WeeklyJobRecommendationDTO> latest = listLatest();
        if (!latest.isEmpty()) {
            return latest;
        }
        return refreshForWeek(LocalDate.now(), authorization);
    }

    @Transactional
    public List<WeeklyJobRecommendationDTO> refreshCurrentWeek(String authorization) {
        return refreshForWeek(LocalDate.now(), authorization);
    }

    @Transactional
    public List<WeeklyJobRecommendationDTO> refreshForWeek(LocalDate date, String authorization) {
        LocalDate weekStart = resolveWeekStart(date == null ? LocalDate.now() : date);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<WeeklyJobRecommendationClient.GeneratedJobRecommendation> generated;
        try {
            generated = client.generateRecommendations(authorization);
        } catch (Exception exception) {
            throw new IllegalStateException("生成每周热门岗位推荐失败：" + exception.getMessage(), exception);
        }

        List<WeeklyJobRecommendation> rows = toEntities(generated, weekStart, weekEnd);
        if (rows.isEmpty()) {
            throw new IllegalStateException("AI 未返回有效岗位推荐");
        }

        repository.deleteByWeekStartDate(weekStart);
        return toDtoList(repository.saveAll(rows));
    }

    private LocalDate resolveWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private List<WeeklyJobRecommendation> toEntities(
            List<WeeklyJobRecommendationClient.GeneratedJobRecommendation> generated,
            LocalDate weekStart,
            LocalDate weekEnd) {
        if (generated == null || generated.isEmpty()) {
            return List.of();
        }

        LocalDateTime generatedAt = LocalDateTime.now();
        List<WeeklyJobRecommendation> rows = new ArrayList<>();
        for (WeeklyJobRecommendationClient.GeneratedJobRecommendation item : generated) {
            if (rows.size() >= MAX_RECOMMENDATION_COUNT) {
                break;
            }
            if (item == null || isBlank(item.jobTitle()) || isBlank(item.salary()) || isBlank(item.skills())) {
                continue;
            }
            String jobTitle = item.jobTitle().trim();
            WeeklyJobRecommendation row = new WeeklyJobRecommendation();
            row.setWeekStartDate(weekStart);
            row.setWeekEndDate(weekEnd);
            row.setSortOrder(rows.size() + 1);
            row.setJobTitle(jobTitle);
            row.setSalary(item.salary().trim());
            row.setSkills(item.skills().trim());
            row.setRecruitmentLink(buildRecruitmentSearchLink(jobTitle));
            row.setSource(SOURCE);
            row.setModelName("weekly_job_recommendation_agent");
            row.setGeneratedAt(generatedAt);
            rows.add(row);
        }
        return rows;
    }

    private WeeklyJobRecommendationDTO toDto(WeeklyJobRecommendation row) {
        WeeklyJobRecommendationDTO dto = new WeeklyJobRecommendationDTO();
        dto.setId(row.getId());
        dto.setWeekStartDate(row.getWeekStartDate());
        dto.setWeekEndDate(row.getWeekEndDate());
        dto.setSortOrder(row.getSortOrder());
        dto.setJobTitle(row.getJobTitle());
        dto.setSalary(row.getSalary());
        dto.setSkills(row.getSkills());
        dto.setRecruitmentLink(buildRecruitmentSearchLink(row.getJobTitle()));
        dto.setSource(row.getSource());
        dto.setModelName(row.getModelName());
        dto.setGeneratedAt(row.getGeneratedAt());
        return dto;
    }

    public WeeklyJobRecommendationDTO toRecommendationDto(WeeklyJobRecommendation row) {
        return row == null ? null : toDto(row);
    }

    private List<WeeklyJobRecommendationDTO> toDtoList(Iterable<WeeklyJobRecommendation> rows) {
        if (rows == null) {
            return List.of();
        }
        List<WeeklyJobRecommendationDTO> result = new ArrayList<>();
        for (WeeklyJobRecommendation row : rows) {
            if (row != null) {
                result.add(toDto(row));
            }
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String buildRecruitmentSearchLink(String jobTitle) {
        String keyword = isBlank(jobTitle) ? "软件工程师" : jobTitle.trim();
        return BOSS_JOB_SEARCH_URL + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }
}
