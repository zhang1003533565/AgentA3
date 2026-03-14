package com.example.appbackend.service.impl;

import com.example.appbackend.dto.CategoryRequest;
import com.example.appbackend.dto.CategoryResponse;
import com.example.appbackend.entity.ActivityCategory;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityCategoryRepository;
import com.example.appbackend.service.ActivitiyCategoryService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ActivityCategoryServiceImpl implements ActivitiyCategoryService {
    @Autowired
    ActivityCategoryRepository activityCategoryRepository;
    @Override
    public CategoryResponse addCategory(CategoryRequest categoryRequest) {
        if(activityCategoryRepository.existsActivityCategoryByCategoryName(categoryRequest.getCategoryName())){
            throw new BusinessException(Result.FORBIDDEN_CODE,"已有该分类");
        }
        ActivityCategory activityCategory =new ActivityCategory();
        activityCategory.setCategoryName(categoryRequest.getCategoryName());
        activityCategory.setSort(categoryRequest.getSort() != null ? categoryRequest.getSort() : 0);
        activityCategory.setStatus(categoryRequest.getStatus() != null ? categoryRequest.getStatus() : 1);
        activityCategoryRepository.save(activityCategory);
        return new CategoryResponse( activityCategory.getCategoryName(),
                activityCategory.getSort(), activityCategory.getStatus(), activityCategory.getCreateTime());

    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        List<ActivityCategory> categories = activityCategoryRepository.findAll();
        return categories.stream()
                .map(cat -> new CategoryResponse( cat.getCategoryName(),
                        cat.getSort(), cat.getStatus(), cat.getCreateTime()))
                .collect(Collectors.toList());
    }
}
