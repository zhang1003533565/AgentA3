package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.FacilityReview;

public interface ReviewService {

    FacilityReview createReview(ReviewRequest request, Long userId);

    ReviewPageResponse getReviewList(Long facilityId, Integer pageNum, Integer pageSize);

    void deleteReview(Long id, Long userId);
}
