package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.FacilityReview;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.FacilityReviewRepository;
import com.example.appbackend.repository.MapMarkerRepository;
import com.example.appbackend.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private FacilityReviewRepository reviewRepository;

    @Autowired
    private MapMarkerRepository mapMarkerRepository;

    @Override
    public FacilityReview createReview(ReviewRequest request, Long userId) {
        mapMarkerRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new BusinessException(404, "设施不存在"));

        FacilityReview review = new FacilityReview();
        review.setFacilityId(request.getFacilityId());
        review.setUserId(userId);
        review.setScore(request.getScore());
        review.setContent(request.getContent());
        review.setImages(request.getImages());
        review.setCreateTime(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    @Override
    public ReviewPageResponse getReviewList(Long facilityId, Integer pageNum, Integer pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<FacilityReview> page = facilityId != null
                ? reviewRepository.findByFacilityId(facilityId, pageRequest)
                : reviewRepository.findAll(pageRequest);

        Double avgScore = facilityId != null ? reviewRepository.findAvgScoreByFacilityId(facilityId) : null;
        Map<String, Integer> scoreDist = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) scoreDist.put(String.valueOf(i), 0);
        if (facilityId != null) {
            Object[] dist = reviewRepository.findScoreDistributionByFacilityId(facilityId);
            if (dist != null) {
                for (Object row : dist) {
                    if (row instanceof Object[]) {
                        Object[] arr = (Object[]) row;
                        if (arr[0] != null && arr[1] != null) {
                            scoreDist.put(String.valueOf(arr[0]), ((Number) arr[1]).intValue());
                        }
                    }
                }
            }
        }

        List<ReviewListItem> items = page.getContent().stream()
                .map(r -> {
                    ReviewListItem item = new ReviewListItem();
                    item.setId(r.getId());
                    item.setFacilityId(r.getFacilityId());
                    item.setUserId(r.getUserId());
                    item.setUserName("");
                    item.setUserAvatar("");
                    item.setScore(r.getScore());
                    item.setContent(r.getContent());
                    item.setImages(r.getImages());
                    item.setCreateTime(r.getCreateTime());
                    return item;
                }).collect(Collectors.toList());

        ReviewPageResponse resp = new ReviewPageResponse();
        resp.setTotal(page.getTotalElements());
        resp.setAvgScore(avgScore);
        resp.setScoreDistribution(scoreDist);
        resp.setList(items);
        return resp;
    }

    @Override
    public void deleteReview(Long id, Long userId) {
        FacilityReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评价不存在"));
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权限删除该评价");
        }
        reviewRepository.delete(review);
    }
}
