package com.example.appbackend.service;

import com.example.appbackend.dto.CategoryRequest;
import com.example.appbackend.dto.CategoryResponse;
import jakarta.validation.Valid;
import java.util.List;

public interface ActivitiyCategoryService {
    CategoryResponse addCategory(@Valid CategoryRequest categoryRequest);

    List<CategoryResponse> getAllCategories();
}
