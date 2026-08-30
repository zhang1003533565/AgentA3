package com.example.appbackend.service;

import com.example.appbackend.dto.CampusCourseDTO;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CareerExplorationService {
    private static final BigDecimal PASS_PERCENT = new BigDecimal("60");
    private final CareerNebulaService nebulaService;
    private final CampusCourseService courseService;
    private final CampusCourseRepository courseRepository;
    private final CampusCourseChapterRepository chapterRepository;
    private final CampusCourseMaterialRepository materialRepository;
    private final CareerChapterProgressRepository chapterProgressRepository;
    private final CampusCourseExamRepository courseExamRepository;
    private final ExamPaperRepository paperRepository;
    private final ExamPaperAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;

    public CareerExplorationService(CareerNebulaService nebulaService, CampusCourseService courseService,
            CampusCourseRepository courseRepository, CampusCourseChapterRepository chapterRepository,
            CampusCourseMaterialRepository materialRepository, CareerChapterProgressRepository chapterProgressRepository,
            CampusCourseExamRepository courseExamRepository, ExamPaperRepository paperRepository,
            ExamPaperAttemptRepository attemptRepository, ObjectMapper objectMapper) {
        this.nebulaService = nebulaService; this.courseService = courseService;
        this.courseRepository = courseRepository; this.chapterRepository = chapterRepository;
        this.materialRepository = materialRepository; this.chapterProgressRepository = chapterProgressRepository;
        this.courseExamRepository = courseExamRepository; this.paperRepository = paperRepository;
        this.attemptRepository = attemptRepository; this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> career(String careerId, Long userId) {
        Map<String, Object> map = nebulaService.getMap();
        Map<String, Object> career = findNode(map.get("careers"), careerId, "岗位不存在");
        List<Map<String, Object>> planets = mapList(map.get("skills")).stream()
                .filter(skill -> careerId.equals(text(skill.get("careerId"), "testing")))
                .filter(skill -> "enabled".equals(text(skill.get("status"), "enabled")))
                .map(skill -> planetSummary(skill, careerId, userId)).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("careerId", careerId); result.put("careerName", career.get("name")); result.put("career", career);
        result.put("explorationProgress", planets.isEmpty() ? 0 : (int) Math.round(planets.stream()
                .mapToInt(p -> number(p.get("explorationProgress"))).average().orElse(0)));
        result.put("planets", planets); result.put("edges", mapList(map.get("edges")));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> planet(String careerId, String skillId, Long userId) {
        Map<String, Object> skill = requireSkill(careerId, skillId); Long courseId = courseId(skill);
        CampusCourseDTO.CourseDetail course = courseService.studentDetail(courseId, userId);
        Map<String, Object> result = new LinkedHashMap<>(planetSummary(skill, careerId, userId));
        result.put("careerId", careerId); result.put("skillId", skillId); result.put("courseName", course.getName());
        result.put("description", course.getDescription());
        result.put("chapters", chapterCards(careerId, skillId, course.getChapters(), userId));
        result.put("finalExam", finalExam(courseId, userId)); return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> chapter(String careerId, String skillId, Long chapterId, Long userId) {
        Map<String, Object> skill = requireSkill(careerId, skillId); Long courseId = courseId(skill);
        courseService.studentDetail(courseId, userId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        CareerChapterProgress progress = progress(careerId, skillId, courseId, chapterId, userId).orElse(null);
        CampusCourseMaterial video = materials(chapter.getMaterialIds()).stream().filter(this::isVideo).findFirst().orElse(null);
        List<Map<String, Object>> attachments = new ArrayList<>();
        materials(chapter.getAdditionalMaterialIds()).forEach(m -> attachments.add(materialView(m)));
        materials(chapter.getWordMaterialIds()).forEach(m -> attachments.add(materialView(m)));
        List<CampusCourseChapter> ordered = chapterRepository.findByCourseIdOrderBySortOrderAscIdAsc(courseId);
        Long nextChapterId = null;
        for (int i = 0; i < ordered.size() - 1; i++) if (ordered.get(i).getId().equals(chapterId)) nextChapterId = ordered.get(i + 1).getId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chapterId", chapter.getId()); result.put("title", chapter.getTitle());
        result.put("summary", chapter.getSummary()); result.put("content", chapter.getContent()); result.put("required", chapter.getRequired());
        result.put("video", videoView(video, progress)); result.put("materials", attachments);
        result.put("questions", publicQuestions(chapter.getQaJson()));
        result.put("quizCompleted", progress != null && Boolean.TRUE.equals(progress.getQuizCompleted()));
        result.put("chapterCompleted", progress != null && Boolean.TRUE.equals(progress.getChapterCompleted()));
        result.put("nextChapterId", nextChapterId); return result;
    }

    @Transactional
    public Map<String, Object> updateVideoProgress(String careerId, String skillId, Long chapterId,
            Long materialId, int currentSeconds, int durationSeconds, int watchedSecondsDelta, Long userId) {
        Map<String, Object> skill = requireSkill(careerId, skillId); Long courseId = courseId(skill);
        CampusCourseMaterial video = materials(requireChapter(courseId, chapterId).getMaterialIds()).stream()
                .filter(item -> item.getId().equals(materialId) && isVideo(item)).findFirst()
                .orElseThrow(() -> new BusinessException(400, "视频不属于当前章节"));
        CareerChapterProgress progress = progress(careerId, skillId, courseId, chapterId, userId).orElseGet(CareerChapterProgress::new);
        initialize(progress, careerId, skillId, courseId, chapterId, userId);
        int trustedDuration = video.getDurationSeconds() != null && video.getDurationSeconds() > 0 ? video.getDurationSeconds() : Math.max(0, durationSeconds);
        progress.setVideoMaterialId(materialId); progress.setVideoDurationSeconds(trustedDuration);
        progress.setVideoPositionSeconds(Math.max(0, Math.min(currentSeconds, trustedDuration > 0 ? trustedDuration : currentSeconds)));
        progress.setEffectiveWatchedSeconds(Math.min(trustedDuration > 0 ? trustedDuration : Integer.MAX_VALUE,
                progress.getEffectiveWatchedSeconds() + Math.max(0, Math.min(25, watchedSecondsDelta))));
        progress.setVideoCompleted(videoRequirementMet(progress)); chapterProgressRepository.save(progress);
        return videoView(video, progress);
    }

    @Transactional
    public Map<String, Object> completeChapter(String careerId, String skillId, Long chapterId, Long userId) {
        Map<String, Object> skill = requireSkill(careerId, skillId); Long courseId = courseId(skill);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        boolean hasVideo = materials(chapter.getMaterialIds()).stream().anyMatch(this::isVideo);
        CareerChapterProgress progress = progress(careerId, skillId, courseId, chapterId, userId).orElseGet(CareerChapterProgress::new);
        initialize(progress, careerId, skillId, courseId, chapterId, userId);
        if (!quizItems(chapter.getQaJson()).isEmpty() && !Boolean.TRUE.equals(progress.getQuizCompleted()))
            throw new BusinessException(409, "请先正确回答本章问题");
        progress.setVideoCompleted(!hasVideo || videoRequirementMet(progress)); progress.setChapterCompleted(true);
        progress.setCompletedAt(LocalDateTime.now()); chapterProgressRepository.save(progress);
        return planet(careerId, skillId, userId);
    }

    @Transactional
    public Map<String, Object> answerChapterQuestion(String careerId, String skillId, Long chapterId,
            String questionId, String submittedAnswer, Long userId) {
        Map<String, Object> skill = requireSkill(careerId, skillId); Long courseId = courseId(skill);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        List<Map<String, Object>> questions = quizItems(chapter.getQaJson());
        int index;
        try { index = Integer.parseInt(questionId.replace("q", "")) - 1; }
        catch (RuntimeException error) { throw new BusinessException(404, "章节问题不存在"); }
        if (index < 0 || index >= questions.size()) throw new BusinessException(404, "章节问题不存在");
        CareerChapterProgress progress = progress(careerId, skillId, courseId, chapterId, userId).orElseGet(CareerChapterProgress::new);
        initialize(progress, careerId, skillId, courseId, chapterId, userId);
        Map<String, Object> question = questions.get(index);
        String correctAnswer = text(question.get("answer"), "").trim();
        String submitted = submittedAnswer == null ? "" : submittedAnswer.trim();
        boolean correct = !correctAnswer.isEmpty() && correctAnswer.equalsIgnoreCase(submitted);
        progress.setQuizCompleted(correct && index == questions.size() - 1);
        chapterProgressRepository.save(progress);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", questionId); result.put("correct", correct);
        result.put("submittedAnswer", submitted); result.put("correctAnswer", correctAnswer);
        result.put("explanation", text(question.get("explanation"), correctAnswer));
        result.put("quizCompleted", progress.getQuizCompleted()); return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> finalExamEntry(String careerId, String skillId, Long userId) {
        Map<String, Object> skill = requireSkill(careerId, skillId); Map<String, Object> exam = finalExam(courseId(skill), userId);
        boolean unlocked = number(planetSummary(skill, careerId, userId).get("chapterProgress")) == 60 && Boolean.TRUE.equals(exam.get("available"));
        exam.put("unlocked", unlocked); exam.put("lockedReason", unlocked ? null : "完成全部必修章节后开放"); return exam;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> syncFinalExam(String careerId, String skillId, Long attemptId, Long userId) {
        Map<String, Object> skill = requireSkill(careerId, skillId); Long courseId = courseId(skill);
        ExamPaperAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new BusinessException(404, "考试记录不存在"));
        if (attempt.getStatus() == ExamPaperAttempt.Status.IN_PROGRESS) throw new BusinessException(409, "考试尚未交卷");
        if (courseExamRepository.findByCourseIdOrderBySortOrderAscIdAsc(courseId).stream().noneMatch(link -> link.getPaperId().equals(attempt.getPaperId())))
            throw new BusinessException(400, "该考试不是当前课程的期末考试");
        Map<String, Object> result = new LinkedHashMap<>(planetSummary(skill, careerId, userId));
        BigDecimal score = scorePercent(attempt).setScale(0, RoundingMode.HALF_UP);
        result.put("score", score); result.put("passingScore", 60); result.put("passed", score.compareTo(PASS_PERCENT) >= 0); return result;
    }

    private Map<String, Object> planetSummary(Map<String, Object> skill, String careerId, Long userId) {
        Map<String, Object> result = new LinkedHashMap<>(skill);
        result.put("nodeStatus", text(skill.get("status"), "enabled"));
        Long courseId = nullableLong(skill.get("courseId"));
        if (courseId == null || courseRepository.findById(courseId).isEmpty()) return unconfigured(result, "尚未关联有效课程");
        CampusCourseDTO.CourseDetail detail;
        try { detail = courseService.studentDetail(courseId, userId); }
        catch (RuntimeException error) { return unconfigured(result, "课程未发布，或当前学生不在课程学习范围内"); }
        List<CampusCourseDTO.ChapterView> required = detail.getChapters().stream().filter(c -> !Boolean.FALSE.equals(c.getRequired())).toList();
        Set<Long> completed = chapterProgressRepository.findByUserIdAndCareerIdAndSkillId(userId, careerId, text(skill.get("id"), ""))
                .stream().filter(p -> Boolean.TRUE.equals(p.getChapterCompleted())).map(CareerChapterProgress::getChapterId)
                .collect(java.util.stream.Collectors.toSet());
        long done = required.stream().filter(c -> completed.contains(c.getId())).count();
        int chapterProgress = required.isEmpty() ? 0 : (int) Math.floor(done * 60.0 / required.size());
        Map<String, Object> exam = finalExam(courseId, userId); boolean passed = Boolean.TRUE.equals(exam.get("passed"));
        int total = Math.min(100, chapterProgress + (passed ? 40 : 0)); boolean attempted = number(exam.get("attemptCount")) > 0;
        String progressStatus = total == 100 ? "completed" : chapterProgress == 60 && attempted ? "final_exam_failed"
                : chapterProgress == 60 && Boolean.TRUE.equals(exam.get("available")) ? "final_exam_ready" : total > 0 ? "learning" : "not_started";
        result.put("configured", true); result.put("courseId", courseId); result.put("name", detail.getName()); result.put("courseName", detail.getName());
        result.put("description", detail.getDescription()); result.put("coverUrl", detail.getCoverUrl());
        result.put("chapterCount", required.size()); result.put("requiredChapterCount", required.size()); result.put("completedChapterCount", done);
        result.put("chapterProgress", chapterProgress); result.put("examProgress", passed ? 40 : 0); result.put("explorationProgress", total);
        result.put("finalExamUnlocked", chapterProgress == 60 && Boolean.TRUE.equals(exam.get("available")));
        result.put("finalExamPassed", passed); result.put("progressStatus", progressStatus); return result;
    }

    private Map<String, Object> unconfigured(Map<String, Object> result, String reason) { result.put("configured", false); result.put("configurationReason", reason); result.put("explorationProgress", 0); result.put("progressStatus", "not_started"); return result; }
    private List<Map<String, Object>> chapterCards(String careerId, String skillId, List<CampusCourseDTO.ChapterView> chapters, Long userId) {
        Map<Long, CareerChapterProgress> saved = new HashMap<>(); chapterProgressRepository.findByUserIdAndCareerIdAndSkillId(userId, careerId, skillId).forEach(p -> saved.put(p.getChapterId(), p));
        List<Map<String, Object>> result = new ArrayList<>(); for (CampusCourseDTO.ChapterView chapter : chapters) { CareerChapterProgress p = saved.get(chapter.getId()); Map<String,Object> item=new LinkedHashMap<>(); item.put("id",chapter.getId());item.put("title",chapter.getTitle());item.put("summary",chapter.getSummary());item.put("estimatedMinutes",chapter.getEstimatedMinutes());item.put("required",chapter.getRequired());item.put("completed",p!=null&&Boolean.TRUE.equals(p.getChapterCompleted()));item.put("videoProgress",watchedPercent(p));result.add(item);} return result;
    }
    private Map<String, Object> finalExam(Long courseId, Long userId) { Map<String,Object> r=new LinkedHashMap<>();r.put("available",false);r.put("passed",false);r.put("passingScore",60);List<CampusCourseExam> links=courseExamRepository.findByCourseIdOrderBySortOrderAscIdAsc(courseId);if(links.isEmpty())return r;ExamPaper p=paperRepository.findById(links.get(links.size()-1).getPaperId()).orElse(null);if(p==null||!Boolean.TRUE.equals(p.getPublished()))return r;List<ExamPaperAttempt>a=attemptRepository.findByPaperIdAndUserIdAndStatusInOrderBySubmittedAtDesc(p.getId(),userId,List.of(ExamPaperAttempt.Status.SUBMITTED,ExamPaperAttempt.Status.AUTO_SUBMITTED));BigDecimal best=a.stream().map(this::scorePercent).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);r.put("available",true);r.put("paperId",p.getId());r.put("title",p.getTitle());r.put("durationMinutes",p.getDurationMinutes());r.put("questionCount",p.getQuestionCount());r.put("attemptCount",a.size());r.put("bestScore",a.isEmpty()?null:best.setScale(0,RoundingMode.HALF_UP));r.put("passed",best.compareTo(PASS_PERCENT)>=0);return r; }
    private BigDecimal scorePercent(ExamPaperAttempt a){return a.getObjectiveTotalScore()==null||a.getObjectiveTotalScore().signum()<=0?BigDecimal.ZERO:a.getObjectiveScore().multiply(new BigDecimal("100")).divide(a.getObjectiveTotalScore(),2,RoundingMode.HALF_UP);}
    private CampusCourseChapter requireChapter(Long c,Long id){return chapterRepository.findById(id).filter(x->c.equals(x.getCourseId())).orElseThrow(()->new BusinessException(404,"课程章节不存在"));}
    private void initialize(CareerChapterProgress p,String c,String s,Long course,Long chapter,Long user){p.setUserId(user);p.setCareerId(c);p.setSkillId(s);p.setCourseId(course);p.setChapterId(chapter);}
    private boolean videoRequirementMet(CareerChapterProgress p){int d=p.getVideoDurationSeconds();return d>0&&p.getEffectiveWatchedSeconds()>=Math.ceil(d*.8)&&p.getVideoPositionSeconds()>=Math.ceil(d*.9);}
    private int watchedPercent(CareerChapterProgress p){return p==null||p.getVideoDurationSeconds()<=0?0:Math.min(100,(int)Math.floor(p.getEffectiveWatchedSeconds()*100.0/p.getVideoDurationSeconds()));}
    private Optional<CareerChapterProgress> progress(String c,String s,Long course,Long chapter,Long user){return chapterProgressRepository.findByUserIdAndCareerIdAndSkillIdAndCourseIdAndChapterId(user,c,s,course,chapter);}
    private List<CampusCourseMaterial> materials(String json){try{List<Long>ids=objectMapper.readValue(json==null?"[]":json,new TypeReference<>(){});Map<Long,CampusCourseMaterial>found=new HashMap<>();materialRepository.findByIdInAndDeletedFalse(ids).forEach(m->found.put(m.getId(),m));return ids.stream().map(found::get).filter(Objects::nonNull).toList();}catch(Exception ignored){return List.of();}}
    private boolean isVideo(CampusCourseMaterial m){return(m.getMimeType()!=null&&m.getMimeType().startsWith("video/"))||(m.getFileType()!=null&&List.of("mp4","webm","mov","m4v","avi","mkv","flv","m3u8").contains(m.getFileType().toLowerCase()));}
    private List<Map<String,Object>> publicQuestions(String json){List<Map<String,Object>> source=quizItems(json);List<Map<String,Object>> result=new ArrayList<>();for(int i=0;i<source.size();i++){Map<String,Object> item=new LinkedHashMap<>();item.put("id","q"+(i+1));item.put("question",text(source.get(i).get("question"),""));result.add(item);}return result;}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> quizItems(String json){if(json==null||json.isBlank())return List.of();try{Object raw=objectMapper.readValue(json,Object.class);if(raw instanceof List<?> list)return list.stream().filter(Map.class::isInstance).map(item->(Map<String,Object>)item).filter(item->!text(item.get("question"),"").isBlank()).toList();if(raw instanceof Map<?,?> map)return List.of((Map<String,Object>)map);return List.of();}catch(Exception ignored){return List.of();}}
    private Map<String,Object> materialView(CampusCourseMaterial m){return Map.of("materialId",m.getId(),"fileName",m.getFileName(),"url",m.getFileUrl(),"fileType",m.getFileType());}
    private Map<String,Object> videoView(CampusCourseMaterial m,CareerChapterProgress p){if(m==null)return Map.of();Map<String,Object>v=new LinkedHashMap<>();v.put("materialId",m.getId());v.put("fileName",m.getFileName());v.put("url",m.getFileUrl());v.put("positionSeconds",p==null?0:p.getVideoPositionSeconds());v.put("durationSeconds",p==null?m.getDurationSeconds():p.getVideoDurationSeconds());v.put("effectiveWatchedSeconds",p==null?0:p.getEffectiveWatchedSeconds());v.put("watchedPercent",watchedPercent(p));v.put("completed",p!=null&&Boolean.TRUE.equals(p.getVideoCompleted()));return v;}
    private Map<String,Object> requireSkill(String c,String s){Map<String,Object>skill=findNode(nebulaService.getMap().get("skills"),s,"课程星球不存在");if(!c.equals(text(skill.get("careerId"),"testing"))||!"enabled".equals(text(skill.get("status"),"enabled")))throw new BusinessException(404,"课程星球不存在或不可用");return skill;}
    private Long courseId(Map<String,Object>s){Long id=nullableLong(s.get("courseId"));if(id==null||courseRepository.findById(id).isEmpty())throw new BusinessException(409,"该星球尚未关联有效课程");return id;}
    private Map<String,Object> findNode(Object raw,String id,String msg){return mapList(raw).stream().filter(n->id.equals(text(n.get("id"),""))).findFirst().orElseThrow(()->new BusinessException(404,msg));}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> mapList(Object v){if(!(v instanceof List<?>l))return List.of();return l.stream().filter(Map.class::isInstance).map(i->(Map<String,Object>)i).toList();}
    private Long nullableLong(Object v){if(v instanceof Number n)return n.longValue();if(v instanceof String s&&s.matches("\\d+"))return Long.parseLong(s);return null;}
    private String text(Object v,String f){return v==null?f:String.valueOf(v);}private int number(Object v){return v instanceof Number n?n.intValue():0;}
}
