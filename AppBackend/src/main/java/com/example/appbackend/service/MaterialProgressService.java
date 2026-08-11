package com.example.appbackend.service;

import com.example.appbackend.dto.MaterialDTO;
import com.example.appbackend.entity.CampusCourseChapter;
import com.example.appbackend.entity.CampusCourseMaterial;
import com.example.appbackend.entity.LearningMaterialProgress;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.CampusCourseChapterRepository;
import com.example.appbackend.repository.CampusCourseMaterialRepository;
import com.example.appbackend.repository.CampusCourseRepository;
import com.example.appbackend.repository.LearningMaterialProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 精细化学习进度服务（学生端）：进度上报、进度查询，并在章节资料全部完成时同步旧打卡表。
 * 通过注入 CampusCourseService 直接调用其现有 updateProgress 方法，不修改其内部实现，
 * 也不自调 HTTP 接口，从而规避 JWT 鉴权与循环调用问题。
 */
@Service
public class MaterialProgressService {

    private final CampusCourseRepository courseRepository;
    private final CampusCourseChapterRepository chapterRepository;
    private final CampusCourseMaterialRepository materialRepository;
    private final LearningMaterialProgressRepository progressRepository;
    private final MaterialIdsCodec materialIdsCodec;
    private final CampusCourseService campusCourseService;

    public MaterialProgressService(
            CampusCourseRepository courseRepository,
            CampusCourseChapterRepository chapterRepository,
            CampusCourseMaterialRepository materialRepository,
            LearningMaterialProgressRepository progressRepository,
            MaterialIdsCodec materialIdsCodec,
            CampusCourseService campusCourseService
    ) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.materialRepository = materialRepository;
        this.progressRepository = progressRepository;
        this.materialIdsCodec = materialIdsCodec;
        this.campusCourseService = campusCourseService;
    }

    /**
     * 上报某资料的观看进度（watchSeconds 视为累计值，取较大者）。
     * 第一阶段：duration_seconds 默认 0，不做自动完成判定，仅置为“学习中”。
     * 第二阶段（duration>0）：watchSeconds ≥ duration 时自动置为“已完成”，并尝试同步章节完成状态。
     */
    @Transactional
    public void report(Long userId, Long materialId, Integer watchSeconds) {
        CampusCourseMaterial material = materialRepository.findById(materialId)
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                .orElseThrow(() -> new BusinessException(404, "资料不存在或已下架"));

        int reported = watchSeconds == null ? 0 : Math.max(0, watchSeconds);
        LearningMaterialProgress progress = progressRepository
                .findByUserIdAndMaterialId(userId, materialId)
                .orElseGet(LearningMaterialProgress::new);
        progress.setUserId(userId);
        progress.setMaterialId(materialId);
        progress.setWatchSeconds(Math.max(reported, progress.getWatchSeconds() == null ? 0 : progress.getWatchSeconds()));

        int duration = material.getDurationSeconds() == null ? 0 : material.getDurationSeconds();
        boolean completed = duration > 0 && progress.getWatchSeconds() >= duration;
        progress.setStatus(completed
                ? LearningMaterialProgress.STATUS_COMPLETED
                : LearningMaterialProgress.STATUS_LEARNING);
        progressRepository.save(progress);

        if (completed) {
            syncChapterCompletion(userId, material.getCourseId(), materialId);
        }
    }

    @Transactional(readOnly = true)
    public MaterialDTO.CourseProgressView courseProgress(Long userId, Long courseId) {
        if (courseId == null || !courseRepository.existsById(courseId)) {
            throw new BusinessException(404, "课程不存在");
        }
        List<CampusCourseChapter> chapters = chapterRepository.findByCourseIdOrderBySortOrderAscIdAsc(courseId);

        // 收集全部引用的资料 ID，批量加载资料与进度，避免 N+1 查询。
        List<Long> allIds = new ArrayList<>();
        for (CampusCourseChapter chapter : chapters) {
            allIds.addAll(materialIdsCodec.parse(chapter.getMaterialIds()));
        }
        Map<Long, CampusCourseMaterial> materialMap = new HashMap<>();
        Map<Long, LearningMaterialProgress> progressMap = new HashMap<>();
        if (!allIds.isEmpty()) {
            materialRepository.findByIdInAndDeletedFalse(allIds)
                    .forEach(item -> materialMap.put(item.getId(), item));
            progressRepository.findByUserIdAndMaterialIdIn(userId, allIds)
                    .forEach(item -> progressMap.put(item.getMaterialId(), item));
        }

        List<MaterialDTO.ChapterProgressView> chapterViews = new ArrayList<>();
        int courseTotal = 0;
        int courseDone = 0;
        for (CampusCourseChapter chapter : chapters) {
            List<MaterialDTO.MaterialProgressView> materialViews = new ArrayList<>();
            int done = 0;
            for (Long id : materialIdsCodec.parse(chapter.getMaterialIds())) {
                CampusCourseMaterial material = materialMap.get(id);
                if (material == null) {
                    continue; // 已软删除或不存在，过滤
                }
                LearningMaterialProgress progress = progressMap.get(id);
                MaterialDTO.MaterialProgressView view = new MaterialDTO.MaterialProgressView();
                view.setMaterialId(id);
                view.setName(material.getFileName());
                view.setUrl(material.getFileUrl());
                view.setType(material.getFileType());
                view.setDurationSeconds(material.getDurationSeconds());
                view.setWatchSeconds(progress == null ? 0 : progress.getWatchSeconds());
                int status = progress == null
                        ? LearningMaterialProgress.STATUS_NOT_STARTED : progress.getStatus();
                view.setStatus(status);
                if (status == LearningMaterialProgress.STATUS_COMPLETED) {
                    done++;
                }
                materialViews.add(view);
            }
            int total = materialViews.size();

            MaterialDTO.ChapterProgressView chapterView = new MaterialDTO.ChapterProgressView();
            chapterView.setChapterId(chapter.getId());
            chapterView.setTitle(chapter.getTitle());
            chapterView.setTotalCount(total);
            chapterView.setCompletedCount(done);
            chapterView.setPercent(total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
            chapterView.setMaterials(materialViews);
            chapterViews.add(chapterView);

            courseTotal += total;
            courseDone += done;
        }

        MaterialDTO.CourseProgressView view = new MaterialDTO.CourseProgressView();
        view.setCourseId(courseId);
        view.setTotalCount(courseTotal);
        view.setCompletedCount(courseDone);
        view.setPercent(courseTotal == 0 ? 0 : (int) Math.round(courseDone * 100.0 / courseTotal));
        view.setChapters(chapterViews);
        return view;
    }

    /**
     * 当某章节引用的资料全部完成时，调用旧 Service 的 updateProgress 将旧表 completed 置 true。
     * 仅在“章节有资料且全部完成”时触发，避免无意义写入与循环调用。
     */
    private void syncChapterCompletion(Long userId, Long courseId, Long materialId) {
        List<CampusCourseChapter> chapters = chapterRepository.findByCourseIdOrderBySortOrderAscIdAsc(courseId);
        for (CampusCourseChapter chapter : chapters) {
            List<Long> ids = materialIdsCodec.parse(chapter.getMaterialIds());
            if (!ids.contains(materialId)) {
                continue;
            }
            List<CampusCourseMaterial> materials = materialRepository.findByIdInAndDeletedFalse(ids);
            if (materials.isEmpty()) {
                continue;
            }
            List<Long> validIds = materials.stream().map(CampusCourseMaterial::getId).toList();
            List<LearningMaterialProgress> progresses =
                    progressRepository.findByUserIdAndMaterialIdIn(userId, validIds);
            long completed = progresses.stream()
                    .filter(item -> item.getStatus() != null
                            && item.getStatus() == LearningMaterialProgress.STATUS_COMPLETED)
                    .count();
            if (completed == validIds.size()) {
                campusCourseService.updateProgress(courseId, chapter.getId(), userId, true);
            }
        }
    }
}
