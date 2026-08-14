package com.example.appbackend.service;

import com.example.appbackend.dto.CampusCourseDTO;
import com.example.appbackend.entity.CampusCourse;
import com.example.appbackend.entity.CampusCourseType;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.CourseMaterialService;
import com.example.appbackend.service.MaterialIdsCodec;
import com.example.appbackend.service.WordParsingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CampusCourseServiceTest {
    private CampusCourseRepository courses;
    private CampusCourseChapterRepository chapters;
    private CampusCourseExamRepository exams;
    private CampusCourseProgressRepository progress;
    private CampusCourseEnrollmentRepository enrollments;
    private ExamPaperRepository papers;
    private UserRepository users;
    private CourseMaterialService materialService;
    private MaterialIdsCodec materialIdsCodec;
    private WordParsingService wordParsingService;
    private CampusCourseMaterialRepository materialRepository;
    private CampusCourseTypeRepository typeRepository;
    private CampusCourseService service;

    @BeforeEach
    void setUp() {
        courses = mock(CampusCourseRepository.class);
        chapters = mock(CampusCourseChapterRepository.class);
        exams = mock(CampusCourseExamRepository.class);
        progress = mock(CampusCourseProgressRepository.class);
        enrollments = mock(CampusCourseEnrollmentRepository.class);
        papers = mock(ExamPaperRepository.class);
        users = mock(UserRepository.class);
        materialService = mock(CourseMaterialService.class);
        materialIdsCodec = mock(MaterialIdsCodec.class);
        wordParsingService = mock(WordParsingService.class);
        materialRepository = mock(CampusCourseMaterialRepository.class);
        typeRepository = mock(CampusCourseTypeRepository.class);
        service = new CampusCourseService(courses, chapters, exams, progress, enrollments, papers, users, materialService, materialIdsCodec, wordParsingService, materialRepository, typeRepository);
        when(chapters.findByCourseIdOrderBySortOrderAscIdAsc(any())).thenReturn(List.of());
        when(exams.findByCourseIdOrderBySortOrderAscIdAsc(any())).thenReturn(List.of());
        when(progress.findByCourseIdAndUserId(any(), any())).thenReturn(List.of());
    }

    @Test
    void publishedCourseRequiresAtLeastOneChapter() {
        CampusCourse course = course(1L, CampusCourse.STATUS_DRAFT, CampusCourse.AUDIENCE_ALL, null);
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        when(chapters.countByCourseId(1L)).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.publish(1L, 9L));
        assertTrue(error.getMessage().contains("至少配置一个课程章节"));
        verify(courses, never()).save(any());
    }

    @Test
    void classScopedStudentOnlySeesMatchingPublishedCourse() {
        CampusCourse matching = course(1L, CampusCourse.STATUS_PUBLISHED, CampusCourse.AUDIENCE_CLASS, "计231,计232");
        CampusCourse hidden = course(2L, CampusCourse.STATUS_PUBLISHED, CampusCourse.AUDIENCE_CLASS, "计233");
        User student = new User();
        student.setId(42L);
        student.setClassName("计231");
        when(users.findById(42L)).thenReturn(Optional.of(student));
        when(users.findById(9L)).thenReturn(Optional.empty());
        when(courses.findByPublishStatusOrderBySortOrderAscPublishTimeDesc(CampusCourse.STATUS_PUBLISHED))
                .thenReturn(List.of(matching, hidden));

        List<CampusCourseDTO.CourseSummary> visible = service.studentList(42L);

        assertEquals(1, visible.size());
        assertEquals(1L, visible.getFirst().getId());
    }

    @Test
    void createCourseTypeRejectsDuplicateName() {
        CampusCourseDTO.CourseTypeSaveRequest request = new CampusCourseDTO.CourseTypeSaveRequest();
        request.setTypeName("竞赛培训");
        when(typeRepository.existsByTypeName("竞赛培训")).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createCourseType(request));
        assertTrue(error.getMessage().contains("名称已存在"));
        verify(typeRepository, never()).save(any());
    }

    @Test
    void createCourseTypeGeneratesCodeAutomatically() {
        CampusCourseDTO.CourseTypeSaveRequest request = new CampusCourseDTO.CourseTypeSaveRequest();
        request.setTypeName("竞赛培训");
        when(typeRepository.existsByTypeName("竞赛培训")).thenReturn(false);
        when(typeRepository.existsByTypeCode(anyString())).thenReturn(false);
        when(typeRepository.save(any(CampusCourseType.class))).thenAnswer(invocation -> {
            CampusCourseType type = invocation.getArgument(0);
            type.setId(9L);
            return type;
        });

        CampusCourseDTO.CourseTypeView view = service.createCourseType(request);

        assertNotNull(view.getId());
        assertEquals("竞赛培训", view.getTypeName());
        assertTrue(view.getTypeCode().startsWith("CT"));
        assertEquals(CampusCourseType.CATEGORY_CUSTOM, view.getCategory());
    }

    private CampusCourse course(Long id, String status, String audienceType, String audienceValues) {
        CampusCourse course = new CampusCourse();
        course.setId(id);
        course.setName("Python程序设计");
        course.setBookTitle("《Python程序设计基础》");
        course.setOwnerId(9L);
        course.setPublishStatus(status);
        course.setAudienceType(audienceType);
        course.setAudienceValues(audienceValues);
        course.setSortOrder(0);
        return course;
    }
}
