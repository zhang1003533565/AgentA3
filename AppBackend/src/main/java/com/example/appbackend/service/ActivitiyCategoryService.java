package com.example.appbackend.service;

import com.example.appbackend.dto.CategoryRequest;
import com.example.appbackend.dto.CategoryResponse;
import jakarta.validation.Valid;

public interface ActivitiyCategoryService {
    CategoryResponse addCategory(@Valid CategoryRequest categoryRequest);
}
