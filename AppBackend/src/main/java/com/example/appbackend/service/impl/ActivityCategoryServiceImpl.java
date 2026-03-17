package com.example.appbackend.service.impl;

import com.example.appbackend.dto.CategoryRequest;
import com.example.appbackend.dto.CategoryResponse;
import com.example.appbackend.entity.ActivityCategory;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ActivityCategoryRepository;
import com.example.appbackend.service.ActivitiyCategoryService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
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
        activityCategory.setSort(categoryRequest.getSort() != null ? categoryRequest.getSort() : 1);
        activityCategory.setStatus(categoryRequest.getStatus() != null ? categoryRequest.getStatus() : 1);
        activityCategoryRepository.save(activityCategory);
        return new CategoryResponse(activityCategory.getId(), activityCategory.getCategoryName(),
                activityCategory.getSort(), activityCategory.getStatus(), activityCategory.getCreateTime());

    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        // 按排序值升序、同序时按 ID 升序
        Sort sort = Sort.by(Sort.Direction.ASC, "sort").and(Sort.by(Sort.Direction.ASC, "id"));
        List<ActivityCategory> categories = activityCategoryRepository.findAll(sort);
        return categories.stream()
                .map(cat -> new CategoryResponse(cat.getId(), cat.getCategoryName(),
                        cat.getSort(), cat.getStatus(), cat.getCreateTime()))
                .collect(Collectors.toList());
    }

    @Override
    public void update(Long id, CategoryRequest categoryRequest) {
        ActivityCategory activityCategory=activityCategoryRepository.findById(id).orElseThrow(()->new BusinessException(Result.FORBIDDEN_CODE,"用户不存在"));
        // 分类名称
        if (categoryRequest.getCategoryName() != null) {
            activityCategory.setCategoryName(categoryRequest.getCategoryName());
        }
// 排序号
        if (categoryRequest.getSort() != null) {
            activityCategory.setSort(categoryRequest.getSort());
        }
// 状态
        if (categoryRequest.getStatus() != null) {
            activityCategory.setStatus(categoryRequest.getStatus());
        }
// 保存到数据库
        activityCategoryRepository.save(activityCategory);
    }

    @Override
    public void delete(Long id) {
        if (!activityCategoryRepository.existsById(id)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "分类不存在");
        }
        activityCategoryRepository.deleteById(id);
    }


}
