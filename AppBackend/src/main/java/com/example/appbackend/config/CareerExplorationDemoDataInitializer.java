package com.example.appbackend.config;

import com.example.appbackend.dto.ExamPaperDTO;
import com.example.appbackend.entity.CampusCourse;
import com.example.appbackend.entity.CampusCourseChapter;
import com.example.appbackend.entity.CampusCourseExam;
import com.example.appbackend.entity.CampusCourseMaterial;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.repository.CampusCourseChapterRepository;
import com.example.appbackend.repository.CampusCourseExamRepository;
import com.example.appbackend.repository.CampusCourseMaterialRepository;
import com.example.appbackend.repository.CampusCourseRepository;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.CareerNebulaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 为岗位探索提供一组可直接体验的幂等演示数据。
 * 只创建不存在的同名课程，并且绝不覆盖管理员已经选择的课程关联。
 */
@Component
@Order(200)
public class CareerExplorationDemoDataInitializer implements ApplicationRunner {
    private static final String QUESTION_BANK_SOURCE_AGENT = "system-practice-bank";
    private static final String LEGACY_GENERATED_SOURCE_AGENT = "career-exploration-demo";
    private static final String PAPER_TITLE = "岗位探索演示期末考试";
    private static final String VIDEO_URL =
            "https://mdn.github.io/shared-assets/videos/flower.mp4#t=0,5";

    private static final List<CourseSeed> COURSE_SEEDS = List.of(
            new CourseSeed("测试基础", "软件测试基础实践",
                    List.of("软件测试流程", "测试类型与方法", "缺陷生命周期")),
            new CourseSeed("Linux 与网络", "Linux 与网络排障实践",
                    List.of("Linux 常用命令", "HTTP 与网络基础", "网络故障排查")),
            new CourseSeed("Web 功能测试", "Web 功能测试实践",
                    List.of("需求分析与测试点", "Web 用例设计", "浏览器兼容性测试")),
            new CourseSeed("自动化测试", "自动化测试入门实践",
                    List.of("自动化测试基础", "页面元素定位", "自动化用例维护")),
            new CourseSeed("性能测试", "性能测试入门实践",
                    List.of("性能指标基础", "压测场景设计", "结果分析与瓶颈定位"))
    );

    private final CampusCourseRepository courseRepository;
    private final CampusCourseChapterRepository chapterRepository;
    private final CampusCourseMaterialRepository materialRepository;
    private final CampusCourseExamRepository courseExamRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamPaperRepository paperRepository;
    private final ExamPaperQuestionRepository paperQuestionRepository;
    private final CareerNebulaService careerNebulaService;
    private final ObjectMapper objectMapper;

    public CareerExplorationDemoDataInitializer(
            CampusCourseRepository courseRepository,
            CampusCourseChapterRepository chapterRepository,
            CampusCourseMaterialRepository materialRepository,
            CampusCourseExamRepository courseExamRepository,
            ExamQuestionRepository questionRepository,
            ExamPaperRepository paperRepository,
            ExamPaperQuestionRepository paperQuestionRepository,
            CareerNebulaService careerNebulaService,
            ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.materialRepository = materialRepository;
        this.courseExamRepository = courseExamRepository;
        this.questionRepository = questionRepository;
        this.paperRepository = paperRepository;
        this.paperQuestionRepository = paperQuestionRepository;
        this.careerNebulaService = careerNebulaService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ExamPaper finalExam = ensureFinalExam();
        Map<String, CampusCourse> coursesByName = courseRepository.findAll().stream()
                .collect(Collectors.toMap(CampusCourse::getName, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));

        int sortOrder = coursesByName.size() + 1;
        for (CourseSeed seed : COURSE_SEEDS) {
            if (coursesByName.containsKey(seed.name())) continue;
            CampusCourse course = createCourse(seed, sortOrder++, finalExam.getId());
            coursesByName.put(course.getName(), course);
        }
        synchronizeCareerPlanets(coursesByName);
    }

    private CampusCourse createCourse(CourseSeed seed, int sortOrder, Long paperId) {
        CampusCourse course = new CampusCourse();
        course.setName(seed.name());
        course.setBookTitle(seed.bookTitle());
        course.setTeacherName("岗位探索教研组");
        course.setLevel("入门");
        course.setDescription("岗位探索演示课程：完成全部章节后解锁期末考试。");
        course.setEstimatedHours(1);
        course.setOwnerId(1L);
        course.setOwnerType("ADMIN");
        course.setCourseType(CampusCourse.COURSE_TYPE_PUBLIC);
        course.setAudienceType(CampusCourse.AUDIENCE_ALL);
        course.setPublishStatus(CampusCourse.STATUS_PUBLISHED);
        course.setPublishedBy(1L);
        course.setPublishTime(LocalDateTime.now());
        course.setSortOrder(sortOrder);
        course = courseRepository.save(course);

        CampusCourseMaterial material = new CampusCourseMaterial();
        material.setCourseId(course.getId());
        material.setFileName(seed.name() + "-5秒演示视频.mp4");
        material.setFileUrl(VIDEO_URL);
        material.setFileSize(1L);
        material.setFileType("mp4");
        material.setMimeType("video/mp4");
        material.setDurationSeconds(5);
        material.setDeleted(false);
        material = materialRepository.save(material);

        for (int index = 0; index < seed.chapters().size(); index++) {
            CampusCourseChapter chapter = new CampusCourseChapter();
            chapter.setCourseId(course.getId());
            chapter.setTitle(seed.chapters().get(index));
            chapter.setSummary("完成本节视频学习并掌握核心知识点。");
            chapter.setContent("本章为岗位探索演示章节点，视频结束后可标记学习完成。");
            chapter.setMaterialIds(json(List.of(material.getId())));
            chapter.setAdditionalMaterialIds("[]");
            chapter.setWordMaterialIds("[]");
            chapter.setEstimatedMinutes(5);
            chapter.setRequired(true);
            chapter.setSortOrder(index + 1);
            chapterRepository.save(chapter);
        }

        CampusCourseExam courseExam = new CampusCourseExam();
        courseExam.setCourseId(course.getId());
        courseExam.setPaperId(paperId);
        courseExam.setChapterScope("全部章节");
        courseExam.setSortOrder(1);
        courseExamRepository.save(courseExam);
        return course;
    }

    private ExamPaper ensureFinalExam() {
        List<ExamQuestion> questionBank = questionRepository
                .findBySourceAgentAndStatusOrderByIdAsc(QUESTION_BANK_SOURCE_AGENT, 1);
        List<QuestionSelection> questions = selectQuestionsFromBank(questionBank);

        ExamPaper paper = paperRepository.findFirstByTitleAndStatus(PAPER_TITLE, 1)
                .orElseGet(ExamPaper::new);
        paper.setTitle(PAPER_TITLE);
        paper.setSubtitle("单选题、多选题、填空题与简答题，总分100分");
        paper.setDurationMinutes(30);
        paper.setPrecautions("完成全部课程章节后参加考试，60分及格。");
        paper.setPageSize(ExamPaperDTO.PageSize.A4);
        paper.setOrientation(ExamPaperDTO.Orientation.PORTRAIT);
        paper.setColumnsCount(1);
        paper.setSelectionMode(ExamPaperDTO.SelectionMode.MANUAL);
        paper.setQuestionCount(questions.size());
        paper.setTotalScore(BigDecimal.valueOf(100));
        paper.setCreatedBy(1L);
        paper.setStatus(1);
        paper.setPublished(true);
        paper.setPublishTime(LocalDateTime.now());
        paper = paperRepository.save(paper);

        List<ExamPaperQuestion> existingQuestions = paperQuestionRepository
                .findByPaperIdOrderBySortOrderAscIdAsc(paper.getId());
        var selectedIds = questions.stream()
                .map(selection -> selection.question().getId())
                .collect(Collectors.toSet());
        var staleSnapshots = existingQuestions.stream()
                .filter(item -> !selectedIds.contains(item.getQuestionId()))
                .toList();
        if (!staleSnapshots.isEmpty()) paperQuestionRepository.deleteAllInBatch(staleSnapshots);
        Map<Long, ExamPaperQuestion> existing = existingQuestions.stream()
                .filter(item -> selectedIds.contains(item.getQuestionId()))
                .collect(Collectors.toMap(ExamPaperQuestion::getQuestionId, Function.identity()));
        List<ExamPaperQuestion> snapshots = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            QuestionSelection selection = questions.get(index);
            ExamQuestion question = selection.question();
            ExamPaperQuestion snapshot = existing.getOrDefault(question.getId(), new ExamPaperQuestion());
            snapshot.setPaperId(paper.getId());
            snapshot.setQuestionId(question.getId());
            snapshot.setSortOrder(index + 1);
            snapshot.setSectionOrder(selection.sectionOrder());
            snapshot.setScore(selection.score());
            snapshot.setType(question.getType());
            snapshot.setStem(question.getStem());
            snapshot.setBodyJson(question.getBodyJson());
            snapshot.setAnswerJson(question.getAnswerJson());
            snapshot.setAnalysis(question.getAnalysis());
            snapshot.setScoringJson(question.getScoringJson());
            snapshots.add(snapshot);
        }
        paperQuestionRepository.saveAll(snapshots);

        List<ExamQuestion> legacyGenerated = questionRepository
                .findBySourceAgentAndStatusOrderByIdAsc(LEGACY_GENERATED_SOURCE_AGENT, 1);
        if (!legacyGenerated.isEmpty()) {
            legacyGenerated.forEach(question -> question.setStatus(0));
            questionRepository.saveAll(legacyGenerated);
        }
        return paper;
    }

    private List<QuestionSelection> selectQuestionsFromBank(List<ExamQuestion> questionBank) {
        List<QuestionGroup> groups = List.of(
                new QuestionGroup("single_choice", 5, BigDecimal.valueOf(6), 1),
                new QuestionGroup("multiple_choice", 3, BigDecimal.valueOf(10), 2),
                new QuestionGroup("fill_blank", 3, BigDecimal.valueOf(10), 3),
                new QuestionGroup("short_answer", 2, BigDecimal.valueOf(5), 4)
        );
        List<QuestionSelection> selections = new ArrayList<>();
        for (QuestionGroup group : groups) {
            List<ExamQuestion> candidates = questionBank.stream()
                    .filter(question -> group.type().equals(question.getType()))
                    .limit(group.count())
                    .toList();
            if (candidates.size() < group.count()) {
                throw new IllegalStateException("题库中的" + group.type() + "题型数量不足" + group.count() + "道");
            }
            candidates.forEach(question -> selections.add(
                    new QuestionSelection(question, group.score(), group.sectionOrder())));
        }
        return selections;
    }

    @SuppressWarnings("unchecked")
    private void synchronizeCareerPlanets(Map<String, CampusCourse> coursesByName) {
        Map<String, Object> map = careerNebulaService.getMap();
        Object rawSkills = map.get("skills");
        if (!(rawSkills instanceof List<?> skills)) return;

        Map<Long, CampusCourse> coursesById = coursesByName.values().stream()
                .collect(Collectors.toMap(CampusCourse::getId, Function.identity(), (first, ignored) -> first));
        boolean changed = false;
        for (Object rawSkill : skills) {
            if (!(rawSkill instanceof Map<?, ?> source)) continue;
            Map<String, Object> skill = (Map<String, Object>) source;
            Long courseId = asLong(skill.get("courseId"));
            CampusCourse linkedCourse = courseId == null ? null : coursesById.get(courseId);
            if (linkedCourse == null && courseId == null) {
                linkedCourse = coursesByName.get(String.valueOf(skill.get("name")));
                if (linkedCourse != null) {
                    skill.put("courseId", linkedCourse.getId());
                    changed = true;
                }
            }
            if (linkedCourse != null && !linkedCourse.getName().equals(skill.get("name"))) {
                skill.put("name", linkedCourse.getName());
                changed = true;
            }
        }
        if (changed) careerNebulaService.saveMap(map);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("岗位探索演示数据无法序列化", error);
        }
    }

    private record CourseSeed(String name, String bookTitle, List<String> chapters) { }
    private record QuestionGroup(String type, int count, BigDecimal score, int sectionOrder) { }
    private record QuestionSelection(ExamQuestion question, BigDecimal score, int sectionOrder) { }
}
