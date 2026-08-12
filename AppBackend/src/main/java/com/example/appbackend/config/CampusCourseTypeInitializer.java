package com.example.appbackend.config;

import com.example.appbackend.entity.CampusCourseType;
import com.example.appbackend.repository.CampusCourseTypeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首次启动时写入内置课程类型（与 campus_course.course_type 的存量取值保持一致）。
 */
@Component
public class CampusCourseTypeInitializer implements ApplicationRunner {

    private final CampusCourseTypeRepository typeRepository;

    public CampusCourseTypeInitializer(CampusCourseTypeRepository typeRepository) {
        this.typeRepository = typeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (typeRepository.count() > 0) {
            return;
        }
        typeRepository.saveAll(List.of(
                builtin("REQUIRED", "必修课", 1),
                builtin("ELECTIVE", "选修课", 2),
                builtin("PUBLIC", "公共课", 3),
                builtin("LAB", "实验课", 4)
        ));
    }

    private CampusCourseType builtin(String code, String name, int sortOrder) {
        CampusCourseType type = new CampusCourseType();
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setCategory(CampusCourseType.CATEGORY_BUILTIN);
        type.setSortOrder(sortOrder);
        return type;
    }
}
