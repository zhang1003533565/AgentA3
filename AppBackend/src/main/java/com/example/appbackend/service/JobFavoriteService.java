package com.example.appbackend.service;

import com.example.appbackend.dto.WeeklyJobRecommendationDTO;
import com.example.appbackend.entity.JobFavorite;
import com.example.appbackend.entity.WeeklyJobRecommendation;
import com.example.appbackend.repository.JobFavoriteRepository;
import com.example.appbackend.repository.WeeklyJobRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class JobFavoriteService {

    private final JobFavoriteRepository jobFavoriteRepository;
    private final WeeklyJobRecommendationRepository weeklyJobRecommendationRepository;
    private final WeeklyJobRecommendationService weeklyJobRecommendationService;

    public JobFavoriteService(
            JobFavoriteRepository jobFavoriteRepository,
            WeeklyJobRecommendationRepository weeklyJobRecommendationRepository,
            WeeklyJobRecommendationService weeklyJobRecommendationService) {
        this.jobFavoriteRepository = jobFavoriteRepository;
        this.weeklyJobRecommendationRepository = weeklyJobRecommendationRepository;
        this.weeklyJobRecommendationService = weeklyJobRecommendationService;
    }

    @Transactional
    public void addFavorite(Long userId, Long recommendationId) {
        if (jobFavoriteRepository.existsByUserIdAndRecommendationId(userId, recommendationId)) {
            return;
        }
        JobFavorite favorite = new JobFavorite();
        favorite.setUserId(userId);
        favorite.setRecommendationId(recommendationId);
        jobFavoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(Long userId, Long recommendationId) {
        jobFavoriteRepository.deleteByUserIdAndRecommendationId(userId, recommendationId);
    }

    public Set<Long> getFavoriteRecommendationIds(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (JobFavorite favorite : jobFavoriteRepository.findByUserId(userId)) {
            ids.add(favorite.getRecommendationId());
        }
        return ids;
    }

    public List<WeeklyJobRecommendationDTO> listFavoriteJobs(Long userId) {
        List<JobFavorite> favorites = jobFavoriteRepository.findByUserId(userId);
        if (favorites.isEmpty()) {
            return List.of();
        }

        List<Long> recommendationIds = favorites.stream()
                .map(JobFavorite::getRecommendationId)
                .toList();

        List<WeeklyJobRecommendation> jobs = weeklyJobRecommendationRepository.findAllById(recommendationIds);
        List<WeeklyJobRecommendationDTO> result = new ArrayList<>();
        for (WeeklyJobRecommendation job : jobs) {
            if (job != null) {
                result.add(weeklyJobRecommendationService.toRecommendationDto(job));
            }
        }
        return result;
    }
}
