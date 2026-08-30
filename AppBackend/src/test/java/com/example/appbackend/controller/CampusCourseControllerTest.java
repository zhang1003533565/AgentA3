package com.example.appbackend.controller;

import com.example.appbackend.dto.CampusCourseDTO;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.service.CampusCourseService;
import com.example.appbackend.service.CourseMaterialService;
import com.example.appbackend.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CampusCourseControllerTest {
    private CampusCourseService service;
    private CourseMaterialService materialService;
    private SystemConfigService systemConfigService;
    private MockMvc adminMvc;
    private MockMvc appMvc;

    @BeforeEach
    void setUp() {
        service = mock(CampusCourseService.class);
        materialService = mock(CourseMaterialService.class);
        systemConfigService = mock(SystemConfigService.class);
        when(service.adminList()).thenReturn(List.of());
        CampusCourseDTO.CourseSummary course = new CampusCourseDTO.CourseSummary();
        course.setId(8L);
        course.setName("Python程序设计");
        when(service.studentList(42L)).thenReturn(List.of(course));

        adminMvc = MockMvcBuilders.standaloneSetup(new AdminCampusCourseController(service, systemConfigService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        appMvc = MockMvcBuilders.standaloneSetup(new AppCampusCourseController(service, materialService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void courseAdministrationRequiresAdminRole() throws Exception {
        adminMvc.perform(get("/api/admin/campus-courses").requestAttr("userId", 1L))
                .andExpect(status().isForbidden());

        adminMvc.perform(get("/api/admin/campus-courses")
                        .requestAttr("userId", 1L)
                        .requestAttr("role", "ADMIN"))
                .andExpect(status().isOk());
        verify(service).adminList();
    }

    @Test
    void studentCourseListUsesAuthenticatedUser() throws Exception {
        appMvc.perform(get("/api/app/campus-courses"))
                .andExpect(status().isUnauthorized());

        appMvc.perform(get("/api/app/campus-courses").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(8))
                .andExpect(jsonPath("$.data[0].name").value("Python程序设计"));
        verify(service).studentList(42L);
    }
}
