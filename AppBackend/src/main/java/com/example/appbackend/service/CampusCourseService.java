package com.example.appbackend.service;

import com.example.appbackend.dto.CampusCourseDTO;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CampusCourseService {
    private final CampusCourseRepository courseRepository;
    private final CampusCourseChapterRepository chapterRepository;
    private final CampusCourseExamRepository courseExamRepository;
    private final CampusCourseProgressRepository progressRepository;
    private final ExamPaperRepository paperRepository;
    private final UserRepository userRepository;

    public CampusCourseService(
            CampusCourseRepository courseRepository,
            CampusCourseChapterRepository chapterRepository,
            CampusCourseExamRepository courseExamRepository,
            CampusCourseProgressRepository progressRepository,
            ExamPaperRepository paperRepository,
            UserRepository userRepository
    ) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.courseExamRepository = courseExamRepository;
        this.progressRepository = progressRepository;
        this.paperRepository = paperRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CampusCourseDTO.CourseSummary> adminList() {
        return courseRepository.findAllByOrderBySortOrderAscUpdateTimeDesc().stream()
                .map(course -> summary(course, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CampusCourseDTO.CourseDetail adminDetail(Long courseId) {
        return detail(requireCourse(courseId), null, true);
    }

    @Transactional
    public CampusCourseDTO.CourseDetail create(CampusCourseDTO.SaveRequest request, Long adminId) {
        CampusCourse course = new CampusCourse();
        course.setOwnerId(adminId);
        course.setOwnerType("ADMIN");
        course.setPublishStatus(CampusCourse.STATUS_DRAFT);
        apply(course, request);
        return detail(courseRepository.save(course), null, true);
    }

    @Transactional
    public CampusCourseDTO.CourseDetail update(Long courseId, CampusCourseDTO.SaveRequest request) {
        CampusCourse course = requireCourse(courseId);
        apply(course, request);
        return detail(courseRepository.save(course), null, true);
    }

    @Transactional
    public CampusCourseDTO.CourseDetail publish(Long courseId, Long adminId) {
        CampusCourse course = requireCourse(courseId);
        if (chapterRepository.countByCourseId(courseId) == 0) {
            throw new BusinessException(400, "请至少配置一个课程章节后再发布");
        }
        course.setPublishStatus(CampusCourse.STATUS_PUBLISHED);
        course.setPublishedBy(adminId);
        course.setPublishTime(LocalDateTime.now());
        return detail(courseRepository.save(course), null, true);
    }

    @Transactional
    public CampusCourseDTO.CourseDetail offline(Long courseId) {
        CampusCourse course = requireCourse(courseId);
        course.setPublishStatus(CampusCourse.STATUS_OFFLINE);
        return detail(courseRepository.save(course), null, true);
    }

    @Transactional
    public void delete(Long courseId) {
        CampusCourse course = requireCourse(courseId);
        if (CampusCourse.STATUS_PUBLISHED.equals(course.getPublishStatus())) {
            throw new BusinessException(400, "已发布课程请先下架再删除");
        }
        progressRepository.deleteByCourseId(courseId);
        courseExamRepository.deleteByCourseId(courseId);
        chapterRepository.deleteByCourseId(courseId);
        courseRepository.delete(course);
    }

    @Transactional
    public CampusCourseDTO.ChapterView createChapter(
            Long courseId, CampusCourseDTO.ChapterSaveRequest request
    ) {
        requireCourse(courseId);
        CampusCourseChapter chapter = new CampusCourseChapter();
        chapter.setCourseId(courseId);
        apply(chapter, request);
        return chapterView(chapterRepository.save(chapter), null);
    }

    @Transactional
    public CampusCourseDTO.ChapterView updateChapter(
            Long courseId, Long chapterId, CampusCourseDTO.ChapterSaveRequest request
    ) {
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        apply(chapter, request);
        return chapterView(chapterRepository.save(chapter), null);
    }

    @Transactional
    public void deleteChapter(Long courseId, Long chapterId) {
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        progressRepository.deleteByChapterId(chapterId);
        chapterRepository.delete(chapter);
    }

    @Transactional
    public CampusCourseDTO.ExamView linkExam(
            Long courseId, CampusCourseDTO.ExamLinkRequest request, Long adminId
    ) {
        requireCourse(courseId);
        ExamPaper paper = paperRepository.findByIdAndStatus(request.getPaperId(), 1)
                .orElseThrow(() -> new BusinessException(404, "试卷不存在"));
        if (!Objects.equals(paper.getCreatedBy(), adminId)) {
            throw new BusinessException(403, "只能关联当前管理员创建的试卷");
        }
        CampusCourseExam link = courseExamRepository.findByCourseIdAndPaperId(courseId, request.getPaperId())
                .orElseGet(CampusCourseExam::new);
        link.setCourseId(courseId);
        link.setPaperId(request.getPaperId());
        link.setChapterScope(trim(request.getChapterScope(), 300));
        link.setDeadline(request.getDeadline());
        link.setSortOrder(value(request.getSortOrder(), 0));
        return examView(courseExamRepository.save(link), paper);
    }

    @Transactional
    public void unlinkExam(Long courseId, Long linkId) {
        CampusCourseExam link = courseExamRepository.findById(linkId)
                .filter(item -> Objects.equals(item.getCourseId(), courseId))
                .orElseThrow(() -> new BusinessException(404, "课程考试关联不存在"));
        courseExamRepository.delete(link);
    }

    @Transactional(readOnly = true)
    public List<CampusCourseDTO.CourseSummary> studentList(Long userId) {
        User user = requireUser(userId);
        return courseRepository.findByPublishStatusOrderBySortOrderAscPublishTimeDesc(
                        CampusCourse.STATUS_PUBLISHED).stream()
                .filter(course -> accessible(course, user))
                .map(course -> summary(course, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public CampusCourseDTO.CourseDetail studentDetail(Long courseId, Long userId) {
        CampusCourse course = requireCourse(courseId);
        User user = requireUser(userId);
        if (!CampusCourse.STATUS_PUBLISHED.equals(course.getPublishStatus()) || !accessible(course, user)) {
            throw new BusinessException(404, "课程不存在或尚未发布");
        }
        return detail(course, userId, false);
    }

    @Transactional
    public CampusCourseDTO.CourseDetail updateProgress(
            Long courseId, Long chapterId, Long userId, boolean completed
    ) {
        CampusCourse course = requireCourse(courseId);
        User user = requireUser(userId);
        if (!CampusCourse.STATUS_PUBLISHED.equals(course.getPublishStatus()) || !accessible(course, user)) {
            throw new BusinessException(403, "无权学习该课程");
        }
        requireChapter(courseId, chapterId);
        CampusCourseProgress progress = progressRepository
                .findByCourseIdAndChapterIdAndUserId(courseId, chapterId, userId)
                .orElseGet(CampusCourseProgress::new);
        progress.setCourseId(courseId);
        progress.setChapterId(chapterId);
        progress.setUserId(userId);
        progress.setCompleted(completed);
        progress.setCompletedTime(completed ? LocalDateTime.now() : null);
        progressRepository.save(progress);
        return detail(course, userId, false);
    }

    private CampusCourseDTO.CourseDetail detail(CampusCourse course, Long userId, boolean admin) {
        List<CampusCourseProgress> progresses = userId == null
                ? List.of()
                : progressRepository.findByCourseIdAndUserId(course.getId(), userId);
        Map<Long, CampusCourseProgress> progressByChapter = new HashMap<>();
        progresses.forEach(item -> progressByChapter.put(item.getChapterId(), item));

        CampusCourseDTO.CourseDetail view = new CampusCourseDTO.CourseDetail();
        copySummary(summary(course, userId), view);
        view.setChapters(chapterRepository.findByCourseIdOrderBySortOrderAscIdAsc(course.getId()).stream()
                .map(chapter -> chapterView(chapter, progressByChapter.get(chapter.getId())))
                .toList());
        view.setExams(courseExamRepository.findByCourseIdOrderBySortOrderAscIdAsc(course.getId()).stream()
                .map(link -> paperRepository.findByIdAndStatus(link.getPaperId(), 1)
                        .filter(paper -> admin || Boolean.TRUE.equals(paper.getPublished()))
                        .map(paper -> examView(link, paper))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList());
        return view;
    }

    private CampusCourseDTO.CourseSummary summary(CampusCourse course, Long userId) {
        List<CampusCourseChapter> chapters = chapterRepository
                .findByCourseIdOrderBySortOrderAscIdAsc(course.getId());
        Set<Long> completed = userId == null ? Set.of()
                : progressRepository.findByCourseIdAndUserId(course.getId(), userId).stream()
                .filter(item -> Boolean.TRUE.equals(item.getCompleted()))
                .map(CampusCourseProgress::getChapterId)
                .collect(java.util.stream.Collectors.toSet());
        long total = chapters.size();
        long done = chapters.stream().filter(item -> completed.contains(item.getId())).count();

        CampusCourseDTO.CourseSummary view = new CampusCourseDTO.CourseSummary();
        view.setId(course.getId());
        view.setName(course.getName());
        view.setBookTitle(course.getBookTitle());
        view.setCoverUrl(course.getCoverUrl());
        view.setDescription(course.getDescription());
        view.setSemester(course.getSemester());
        view.setEstimatedHours(course.getEstimatedHours());
        view.setOwnerId(course.getOwnerId());
        view.setOwnerName(userRepository.findById(course.getOwnerId())
                .map(user -> firstNonBlank(user.getRealName(), user.getUsername()))
                .orElse("管理员"));
        view.setOwnerType(course.getOwnerType());
        view.setAudienceType(course.getAudienceType());
        view.setAudienceValues(course.getAudienceValues());
        view.setPublishStatus(course.getPublishStatus());
        view.setSortOrder(course.getSortOrder());
        view.setChapterCount(total);
        view.setExamCount(courseExamRepository.findByCourseIdOrderBySortOrderAscIdAsc(course.getId()).size());
        view.setProgressPercent(total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
        view.setCurrentChapterTitle(chapters.stream()
                .filter(item -> !completed.contains(item.getId()))
                .map(CampusCourseChapter::getTitle)
                .findFirst()
                .orElse(total == 0 ? null : "已完成全部章节"));
        view.setPublishTime(course.getPublishTime());
        view.setUpdateTime(course.getUpdateTime());
        return view;
    }

    private CampusCourseDTO.ChapterView chapterView(
            CampusCourseChapter chapter, CampusCourseProgress progress
    ) {
        CampusCourseDTO.ChapterView view = new CampusCourseDTO.ChapterView();
        view.setId(chapter.getId());
        view.setCourseId(chapter.getCourseId());
        view.setTitle(chapter.getTitle());
        view.setSummary(chapter.getSummary());
        view.setContent(chapter.getContent());
        view.setResourceType(chapter.getResourceType());
        view.setResourceUrl(chapter.getResourceUrl());
        view.setEstimatedMinutes(chapter.getEstimatedMinutes());
        view.setRequired(chapter.getRequired());
        view.setSortOrder(chapter.getSortOrder());
        view.setCompleted(progress != null && Boolean.TRUE.equals(progress.getCompleted()));
        view.setCompletedTime(progress == null ? null : progress.getCompletedTime());
        return view;
    }

    private CampusCourseDTO.ExamView examView(CampusCourseExam link, ExamPaper paper) {
        CampusCourseDTO.ExamView view = new CampusCourseDTO.ExamView();
        view.setId(link.getId());
        view.setPaperId(paper.getId());
        view.setTitle(paper.getTitle());
        view.setSubtitle(paper.getSubtitle());
        view.setChapterScope(link.getChapterScope());
        view.setQuestionCount(paper.getQuestionCount());
        view.setDurationMinutes(paper.getDurationMinutes());
        view.setTotalScore(paper.getTotalScore());
        view.setPublished(paper.getPublished());
        view.setDeadline(link.getDeadline());
        view.setSortOrder(link.getSortOrder());
        return view;
    }

    private void apply(CampusCourse course, CampusCourseDTO.SaveRequest request) {
        course.setName(required(request.getName(), "课程名称", 120));
        course.setBookTitle(required(request.getBookTitle(), "课程书名称", 160));
        course.setCoverUrl(trim(request.getCoverUrl(), 500));
        course.setDescription(trim(request.getDescription(), 2000));
        course.setSemester(trim(request.getSemester(), 40));
        course.setEstimatedHours(request.getEstimatedHours());
        String audienceType = firstNonBlank(request.getAudienceType(), CampusCourse.AUDIENCE_ALL)
                .toUpperCase(Locale.ROOT);
        if (!Set.of(CampusCourse.AUDIENCE_ALL, CampusCourse.AUDIENCE_CLASS,
                CampusCourse.AUDIENCE_STUDENT).contains(audienceType)) {
            throw new BusinessException(400, "学习范围类型不合法");
        }
        String audienceValues = normalizeAudience(request.getAudienceValues());
        if (!CampusCourse.AUDIENCE_ALL.equals(audienceType) && audienceValues == null) {
            throw new BusinessException(400, "请填写班级或学生范围");
        }
        course.setAudienceType(audienceType);
        course.setAudienceValues(CampusCourse.AUDIENCE_ALL.equals(audienceType) ? null : audienceValues);
        course.setSortOrder(value(request.getSortOrder(), 0));
    }

    private void apply(CampusCourseChapter chapter, CampusCourseDTO.ChapterSaveRequest request) {
        chapter.setTitle(required(request.getTitle(), "章节标题", 160));
        chapter.setSummary(trim(request.getSummary(), 1000));
        chapter.setContent(request.getContent() == null ? null : request.getContent().trim());
        chapter.setResourceType(trim(request.getResourceType(), 30));
        chapter.setResourceUrl(trim(request.getResourceUrl(), 500));
        chapter.setEstimatedMinutes(request.getEstimatedMinutes());
        chapter.setRequired(request.getRequired() == null || request.getRequired());
        chapter.setSortOrder(value(request.getSortOrder(), 0));
    }

    private boolean accessible(CampusCourse course, User user) {
        if (CampusCourse.AUDIENCE_ALL.equals(course.getAudienceType())) return true;
        Set<String> values = new HashSet<>(Arrays.asList(
                Optional.ofNullable(course.getAudienceValues()).orElse("").split(",")));
        if (CampusCourse.AUDIENCE_CLASS.equals(course.getAudienceType())) {
            return user.getClassName() != null && values.contains(user.getClassName().trim());
        }
        return values.contains(String.valueOf(user.getId()))
                || values.contains(Optional.ofNullable(user.getPersonalNumber()).orElse("").trim())
                || values.contains(Optional.ofNullable(user.getUsername()).orElse("").trim());
    }

    private CampusCourse requireCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "课程不存在"));
    }

    private CampusCourseChapter requireChapter(Long courseId, Long chapterId) {
        return chapterRepository.findById(chapterId)
                .filter(item -> Objects.equals(item.getCourseId(), courseId))
                .orElseThrow(() -> new BusinessException(404, "课程章节不存在"));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
    }

    private void copySummary(
            CampusCourseDTO.CourseSummary source, CampusCourseDTO.CourseSummary target
    ) {
        target.setId(source.getId());
        target.setName(source.getName());
        target.setBookTitle(source.getBookTitle());
        target.setCoverUrl(source.getCoverUrl());
        target.setDescription(source.getDescription());
        target.setSemester(source.getSemester());
        target.setEstimatedHours(source.getEstimatedHours());
        target.setOwnerId(source.getOwnerId());
        target.setOwnerName(source.getOwnerName());
        target.setOwnerType(source.getOwnerType());
        target.setAudienceType(source.getAudienceType());
        target.setAudienceValues(source.getAudienceValues());
        target.setPublishStatus(source.getPublishStatus());
        target.setSortOrder(source.getSortOrder());
        target.setChapterCount(source.getChapterCount());
        target.setExamCount(source.getExamCount());
        target.setProgressPercent(source.getProgressPercent());
        target.setCurrentChapterTitle(source.getCurrentChapterTitle());
        target.setPublishTime(source.getPublishTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private String normalizeAudience(String text) {
        if (text == null) return null;
        String normalized = Arrays.stream(text.split("[,，;；\\n\\r]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        return normalized.isBlank() ? null : normalized;
    }

    private String required(String text, String label, int max) {
        String value = trim(text, max);
        if (value == null) throw new BusinessException(400, label + "不能为空");
        return value;
    }

    private String trim(String text, int max) {
        if (text == null || text.trim().isEmpty()) return null;
        String value = text.trim();
        if (value.length() > max) throw new BusinessException(400, "输入内容过长");
        return value;
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
