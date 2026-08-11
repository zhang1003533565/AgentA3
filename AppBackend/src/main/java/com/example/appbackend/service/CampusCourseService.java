package com.example.appbackend.service;

import com.example.appbackend.dto.CampusCourseDTO;
import com.example.appbackend.dto.MaterialDTO;
import com.example.appbackend.dto.WordContentDTO;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CampusCourseService {
    private final CampusCourseRepository courseRepository;
    private final CampusCourseChapterRepository chapterRepository;
    private final CampusCourseExamRepository courseExamRepository;
    private final CampusCourseProgressRepository progressRepository;
    private final CampusCourseEnrollmentRepository enrollmentRepository;
    private final ExamPaperRepository paperRepository;
    private final UserRepository userRepository;
    private final CourseMaterialService materialService;
    private final MaterialIdsCodec materialIdsCodec;
    private final WordParsingService wordParsingService;
    private final CampusCourseMaterialRepository materialRepository;
    private final CampusCourseTypeRepository typeRepository;

    public CampusCourseService(
            CampusCourseRepository courseRepository,
            CampusCourseChapterRepository chapterRepository,
            CampusCourseExamRepository courseExamRepository,
            CampusCourseProgressRepository progressRepository,
            CampusCourseEnrollmentRepository enrollmentRepository,
            ExamPaperRepository paperRepository,
            UserRepository userRepository,
            CourseMaterialService materialService,
            MaterialIdsCodec materialIdsCodec,
            WordParsingService wordParsingService,
            CampusCourseMaterialRepository materialRepository,
            CampusCourseTypeRepository typeRepository
    ) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.courseExamRepository = courseExamRepository;
        this.progressRepository = progressRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paperRepository = paperRepository;
        this.userRepository = userRepository;
        this.materialService = materialService;
        this.materialIdsCodec = materialIdsCodec;
        this.wordParsingService = wordParsingService;
        this.materialRepository = materialRepository;
        this.typeRepository = typeRepository;
    }

    @Transactional(readOnly = true)
    public List<CampusCourseDTO.CourseTypeView> listCourseTypes() {
        List<CampusCourseDTO.CourseTypeView> types = typeRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::courseTypeView)
                .toList();
        System.out.println("[DEBUG] listCourseTypes: total=" + types.size()
                + " CUSTOM=" + types.stream().filter(t -> "CUSTOM".equals(t.getCategory())).count()
                + " BUILTIN=" + types.stream().filter(t -> "BUILTIN".equals(t.getCategory())).count());
        return types;
    }

    @Transactional
    public CampusCourseDTO.CourseTypeView createCourseType(CampusCourseDTO.CourseTypeSaveRequest request) {
        // 只允许输入类型名称，id 由数据库自增分配，typeCode 由后端自动生成
        String name = required(request.getTypeName(), "类型名称", 20);
        if (typeRepository.existsByTypeName(name)) {
            throw new BusinessException(400, "类型名称已存在，请勿重复创建");
        }
        CampusCourseType type = new CampusCourseType();
        type.setTypeCode(generateTypeCode());
        type.setTypeName(name);
        type.setCategory(CampusCourseType.CATEGORY_CUSTOM);
        type.setSortOrder(value(request.getSortOrder(), 0));
        return courseTypeView(typeRepository.save(type));
    }

    /**
     * 自动生成不重复的类型代码：CT + 6 位大写字母数字，冲突时重试，数据库唯一索引兜底。
     */
    private String generateTypeCode() {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder("CT");
            for (int i = 0; i < 6; i++) {
                code.append(chars.charAt(random.nextInt(chars.length())));
            }
            String candidate = code.toString();
            if (!typeRepository.existsByTypeCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(500, "类型代码生成失败，请重试");
    }

    private CampusCourseDTO.CourseTypeView courseTypeView(CampusCourseType type) {
        CampusCourseDTO.CourseTypeView view = new CampusCourseDTO.CourseTypeView();
        view.setId(type.getId());
        view.setTypeCode(type.getTypeCode());
        view.setTypeName(type.getTypeName());
        view.setCategory(type.getCategory());
        view.setSortOrder(type.getSortOrder());
        return view;
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

    /**
     * 分页获取已发布课程（前端下拉加载更多）。
     * page 从 1 开始。返回 { "list": [...], "hasMore": true/false }
     */
    @Transactional(readOnly = true)
    public Map<String, Object> studentPage(Long userId, int page, int pageSize) {
        User user = requireUser(userId);
        List<CampusCourse> all = courseRepository
                .findByPublishStatusOrderBySortOrderAscPublishTimeDesc(CampusCourse.STATUS_PUBLISHED);
        List<CampusCourseDTO.CourseSummary> accessible = all.stream()
                .filter(course -> accessible(course, user))
                .map(course -> summary(course, userId))
                .toList();
        int total = accessible.size();
        int from = (page - 1) * pageSize;
        if (from >= total) {
            return Map.of("list", List.of(), "hasMore", false);
        }
        int to = Math.min(from + pageSize, total);
        return Map.of("list", accessible.subList(from, to), "hasMore", to < total);
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
        requireEnrolled(courseId, userId);
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

    @Transactional
    public void enroll(Long courseId, Long userId) {
        CampusCourse course = requireCourse(courseId);
        User user = requireUser(userId);
        if (!CampusCourse.STATUS_PUBLISHED.equals(course.getPublishStatus()) || !accessible(course, user)) {
            throw new BusinessException(403, "该课程暂不可加入");
        }
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BusinessException(400, "已加入该课程");
        }
        CampusCourseEnrollment enrollment = new CampusCourseEnrollment();
        enrollment.setUserId(userId);
        enrollment.setCourseId(courseId);
        enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void unenroll(Long courseId, Long userId) {
        requireCourse(courseId);
        requireUser(userId);
        if (!enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BusinessException(400, "尚未加入该课程");
        }
        enrollmentRepository.deleteByUserIdAndCourseId(userId, courseId);
    }

    private void requireEnrolled(Long courseId, Long userId) {
        if (!enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BusinessException(403, "请先加入课程后再学习");
        }
    }

    @Transactional(readOnly = true)
    public List<CampusCourseDTO.CourseSummary> myEnrolledCourses(Long userId) {
        requireUser(userId);
        List<CampusCourseEnrollment> enrollments = enrollmentRepository
                .findByUserIdOrderByEnrolledTimeDesc(userId);
        return enrollments.stream()
                .map(enrollment -> courseRepository.findById(enrollment.getCourseId())
                        .filter(course -> CampusCourse.STATUS_PUBLISHED.equals(course.getPublishStatus()))
                        .map(course -> summary(course, userId))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> chapterDetail(Long courseId, Long chapterId, Long userId) {
        CampusCourse course = requireCourse(courseId);
        User user = requireUser(userId);
        if (!CampusCourse.STATUS_PUBLISHED.equals(course.getPublishStatus()) || !accessible(course, user)) {
            throw new BusinessException(404, "课程不存在或尚未发布");
        }
        requireEnrolled(courseId, userId);
        CampusCourseChapter chapterEntity = requireChapter(courseId, chapterId);
        CampusCourseProgress progress = progressRepository
                .findByCourseIdAndChapterIdAndUserId(courseId, chapterId, userId)
                .orElse(null);
        CampusCourseDTO.ChapterView chapterView = chapterView(chapterEntity, progress);
        // 材料元数据不携带 fileUrl，前端按需通过 GET .../materials/{id}/url 接口获取实际 URL
        List<MaterialDTO.MaterialView> materials = stripFileUrls(materialService.getChapterMaterials(courseId, chapterId));
        List<MaterialDTO.MaterialView> additionalMaterials = stripFileUrls(materialService.getChapterAdditionalMaterials(courseId, chapterId));
        List<MaterialDTO.MaterialView> wordMaterials = stripFileUrls(materialService.getChapterWordMaterials(courseId, chapterId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseName", course.getName());
        result.put("courseId", course.getId());
        result.put("chapter", chapterView);
        result.put("materials", materials);
        result.put("additionalMaterials", additionalMaterials);
        result.put("wordMaterials", wordMaterials);
        return result;
    }

    /**
     * 轻量检查某章节的资源状态（视频/Word/附件是否存在）。
     * 前端进入章节详情前先调用此接口，根据返回的标志决定是否渲染对应占位区域。
     */
    @Transactional(readOnly = true)
    public Map<String, Boolean> chapterResources(Long courseId, Long chapterId, Long userId) {
        CampusCourse course = requireCourse(courseId);
        User user = requireUser(userId);
        if (!CampusCourse.STATUS_PUBLISHED.equals(course.getPublishStatus()) || !accessible(course, user)) {
            throw new BusinessException(404, "课程不存在或尚未发布");
        }
        requireEnrolled(courseId, userId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("hasVideo", !materialIdsCodec.parse(chapter.getMaterialIds()).isEmpty());
        result.put("hasWordDocuments", !materialIdsCodec.parse(chapter.getWordMaterialIds()).isEmpty());
        result.put("hasAttachments", !materialIdsCodec.parse(chapter.getAdditionalMaterialIds()).isEmpty());
        return result;
    }

    /**
     * 获取章节关联的 Word 文档指定分页内容。
     */
    @Transactional(readOnly = true)
    public WordContentDTO.PageResponse wordContent(
            Long courseId, Long chapterId, Long materialId, int page, int pageSize, Long userId
    ) {
        CampusCourse course = requireCourse(courseId);
        User user = requireUser(userId);
        if (!CampusCourse.STATUS_PUBLISHED.equals(course.getPublishStatus()) || !accessible(course, user)) {
            throw new BusinessException(404, "课程不存在或尚未发布");
        }
        requireEnrolled(courseId, userId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        List<Long> wordIds = materialIdsCodec.parse(chapter.getWordMaterialIds());
        if (!wordIds.contains(materialId)) {
            throw new BusinessException(400, "该 Word 资料不属于当前章节");
        }
        CampusCourseMaterial material = materialRepository.findById(materialId)
                .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
                .orElseThrow(() -> new BusinessException(404, "Word 资料不存在或已下架"));
        return wordParsingService.parsePage(material, page, pageSize);
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
        view.setDisplayImageUrl(course.getDisplayImageUrl());
        view.setDescription(course.getDescription());
        view.setSemester(course.getSemester());
        view.setEstimatedHours(course.getEstimatedHours());
        view.setOwnerId(course.getOwnerId());
        view.setOwnerName(userRepository.findById(course.getOwnerId())
                .map(user -> firstNonBlank(user.getRealName(), user.getUsername()))
                .orElse("管理员"));
        view.setOwnerType(course.getOwnerType());
        view.setCourseType(course.getCourseType());
        List<String> customTypeCodes = parseCustomTypes(course.getCustomCourseTypes());
        view.setCustomCourseTypes(customTypeCodes);
        view.setCustomCourseTypeNames(resolveCustomTypeNames(customTypeCodes));
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
        System.out.println("[DEBUG] apply: customCourseTypes=" + request.getCustomCourseTypes());
        course.setName(required(request.getName(), "课程名称", 120));
        course.setBookTitle(required(request.getBookTitle(), "课程书名称", 160));
        course.setCoverUrl(trim(request.getCoverUrl(), 500));
        course.setDisplayImageUrl(trim(request.getDisplayImageUrl(), 500));
        course.setDescription(trim(request.getDescription(), 2000));
        String courseType = required(request.getCourseType(), "必选课程类型", 10);
        boolean builtin = typeRepository.findByTypeCode(courseType)
                .map(type -> CampusCourseType.CATEGORY_BUILTIN.equals(type.getCategory()))
                .orElse(false);
        if (!builtin) {
            throw new BusinessException(400, "必选课程类型不存在或不是内置类型");
        }
        course.setCourseType(courseType);
        String resolved = resolveCustomCourseTypes(request.getCustomCourseTypes());
        System.out.println("[DEBUG] apply: resolved customCourseTypes=" + resolved);
        course.setCustomCourseTypes(resolved);
        course.setSemester(null);
        course.setEstimatedHours(null);
        course.setAudienceType(CampusCourse.AUDIENCE_ALL);
        course.setAudienceValues(null);
        course.setSortOrder(value(request.getSortOrder(), 0));
    }

    /**
     * 校验并拼接自定义课程类型：仅允许已存在的 CUSTOM 类型，去重且最多 20 个，逗号分隔存储。
     */
    private String resolveCustomCourseTypes(List<String> codes) {
        System.out.println("[DEBUG] resolveCustomCourseTypes: input=" + codes);
        if (codes == null || codes.isEmpty()) return null;
        Set<String> seen = new LinkedHashSet<>();
        for (String code : codes) {
            String value = trim(code, 10);
            if (value == null) continue;
            if (seen.size() >= 20) {
                throw new BusinessException(400, "自定义课程类型最多选择 20 个");
            }
            if (!seen.add(value)) continue;
            boolean custom = typeRepository.findByTypeCode(value)
                    .map(type -> CampusCourseType.CATEGORY_CUSTOM.equals(type.getCategory()))
                    .orElse(false);
            if (!custom) {
                throw new BusinessException(400, "自定义课程类型不存在：" + value);
            }
        }
        return seen.isEmpty() ? null : String.join(",", seen);
    }

    private List<String> parseCustomTypes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 将类型代码列表批量查表映射为名称，供前端直接渲染，不依赖类型字典接口单独请求。
     * 若某代码在字典中已不存在（如被删除），兜底回退为原始代码。
     */
    private List<String> resolveCustomTypeNames(List<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        Map<String, String> nameMap = typeRepository.findByTypeCodeIn(codes).stream()
                .collect(java.util.stream.Collectors.toMap(
                        CampusCourseType::getTypeCode,
                        CampusCourseType::getTypeName,
                        (a, b) -> a));
        return codes.stream()
                .map(code -> nameMap.getOrDefault(code, code))
                .toList();
    }

    private void apply(CampusCourseChapter chapter, CampusCourseDTO.ChapterSaveRequest request) {
        chapter.setTitle(required(request.getTitle(), "章节标题", 160));
        chapter.setSummary(trim(request.getSummary(), 1000));
        chapter.setContent(request.getContent() == null ? null : request.getContent().trim());
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
        target.setDisplayImageUrl(source.getDisplayImageUrl());
        target.setDescription(source.getDescription());
        target.setSemester(source.getSemester());
        target.setEstimatedHours(source.getEstimatedHours());
        target.setOwnerId(source.getOwnerId());
        target.setOwnerName(source.getOwnerName());
        target.setOwnerType(source.getOwnerType());
        target.setCourseType(source.getCourseType());
        target.setCustomCourseTypes(source.getCustomCourseTypes());
        target.setCustomCourseTypeNames(source.getCustomCourseTypeNames());
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

    private String required(String text, String label, int max) {
        String value = trim(text, max);
        if (value == null) throw new BusinessException(400, label + "不能为空");
        validateNoGarbled(value, label);
        return value;
    }

    /**
     * 校验文本不包含 Unicode 替换字符（U+FFFD），该字符出现说明数据在传输过程中编码受损。
     * 同时拒绝仅由 ? 组成的乱码字符串。
     */
    private void validateNoGarbled(String text, String label) {
        if (text.indexOf('\uFFFD') >= 0) {
            throw new BusinessException(400, label + "包含无法识别的字符（编码异常），请重新输入");
        }
        // 若去除非中英文/数字后仅剩连续 ? 字符，视为乱码（正常中文不会出现连续大量 ?）
        String printable = text.replaceAll("[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\s（）()【】、，。,.]+", "");
        if (!printable.isEmpty() && printable.trim().matches("^[?？]+$")) {
            throw new BusinessException(400, label + "包含乱码字符，请重新输入");
        }
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

    /** 剥离材料列表中的 fileUrl，前端按需通过 materials/{id}/url 接口获取。 */
    private List<MaterialDTO.MaterialView> stripFileUrls(List<MaterialDTO.MaterialView> views) {
        if (views == null) return List.of();
        return views.stream().map(v -> {
            MaterialDTO.MaterialView copy = new MaterialDTO.MaterialView();
            copy.setId(v.getId());
            copy.setCourseId(v.getCourseId());
            copy.setFileName(v.getFileName());
            copy.setFileUrl(null);
            copy.setFileSize(v.getFileSize());
            copy.setFileType(v.getFileType());
            copy.setMimeType(v.getMimeType());
            copy.setDurationSeconds(v.getDurationSeconds());
            copy.setDeleted(v.getDeleted());
            copy.setUploadBatchId(v.getUploadBatchId());
            copy.setCreatedAt(v.getCreatedAt());
            return copy;
        }).toList();
    }
}
