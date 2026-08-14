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
            seedMajorCategories();
            return;
        }
        typeRepository.saveAll(List.of(
                builtin("REQUIRED", "必修课", 1),
                builtin("ELECTIVE", "选修课", 2),
                builtin("PUBLIC", "公共课", 3),
                builtin("LAB", "实验课", 4)
        ));
        seedMajorCategories();
    }

    private void seedMajorCategories() {
        String[] majors = {"哲学类", "经济学类", "法学类", "教育学类", "文学类", "历史学类",
                "理学类", "工学类", "农学类", "医学类", "管理学类", "艺术学类", "军事学类", "交叉学科类"};
        for (int i = 0; i < majors.length; i++) {
            if (typeRepository.findByTypeCode(majors[i]).isEmpty()) {
                typeRepository.save(custom(majors[i], majors[i], 10 + i));
            }
        }
    }

    private CampusCourseType builtin(String code, String name, int sortOrder) {
        CampusCourseType type = new CampusCourseType();
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setCategory(CampusCourseType.CATEGORY_BUILTIN);
        type.setSortOrder(sortOrder);
        return type;
    }

    private CampusCourseType custom(String code, String name, int sortOrder) {
        CampusCourseType type = new CampusCourseType();
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setCategory(CampusCourseType.CATEGORY_CUSTOM);
        type.setSortOrder(sortOrder);
        return type;
    }
}
