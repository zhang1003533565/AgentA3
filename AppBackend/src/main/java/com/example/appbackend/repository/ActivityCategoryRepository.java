package com.example.appbackend.repository;

import com.example.appbackend.entity.Activity;
import com.example.appbackend.entity.ActivityCategory;
import org.springdoc.core.providers.JavadocProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityCategoryRepository extends JpaRepository<ActivityCategory,Long> {
    boolean existsActivityCategoryByCategoryName(String categoryname);
}
