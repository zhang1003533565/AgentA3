package com.example.appbackend.service;

import com.example.appbackend.config.CourseMaterialProperties;
import com.example.appbackend.dto.MaterialDTO;
import com.example.appbackend.entity.CampusCourseChapter;
import com.example.appbackend.entity.CampusCourseMaterial;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.CampusCourseChapterRepository;
import com.example.appbackend.repository.CampusCourseMaterialRepository;
import com.example.appbackend.repository.CampusCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 课程资料池管理服务（管理端）：文件夹上传、引用检查、安全删除。
 * 仅新增，不修改任何现有控制器/服务；章节 material_ids 通过 Codec 安全读写。
 */
@Service
public class CourseMaterialService {

    /** 视频文件扩展名集合（用于资料池类型校验）。 */
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "webm", "avi", "mkv", "flv", "m3u8");

    /** Word 文档扩展名集合。 */
    private static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx");

    private final CampusCourseRepository courseRepository;
    private final CampusCourseMaterialRepository materialRepository;
    private final CampusCourseChapterRepository chapterRepository;
    private final FileStorageService fileStorageService;
    private final MaterialIdsCodec materialIdsCodec;
    private final CourseMaterialProperties properties;

    public CourseMaterialService(
            CampusCourseRepository courseRepository,
            CampusCourseMaterialRepository materialRepository,
            CampusCourseChapterRepository chapterRepository,
            FileStorageService fileStorageService,
            MaterialIdsCodec materialIdsCodec,
            CourseMaterialProperties properties
    ) {
        this.courseRepository = courseRepository;
        this.materialRepository = materialRepository;
        this.chapterRepository = chapterRepository;
        this.fileStorageService = fileStorageService;
        this.materialIdsCodec = materialIdsCodec;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<MaterialDTO.MaterialView> listMaterials(Long courseId, boolean includeDeleted) {
        requireCourse(courseId);
        List<CampusCourseMaterial> materials = includeDeleted
                ? materialRepository.findByCourseIdOrderByIdDesc(courseId)
                : materialRepository.findByCourseIdAndDeletedFalseOrderByIdDesc(courseId);
        return materials.stream().map(this::toView).toList();
    }

    @Transactional
    public MaterialDTO.FolderUploadResult folderUpload(
            Long courseId, List<MultipartFile> files, String uploadBatchId
    ) {
        requireCourse(courseId);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }

        Set<String> whitelist = properties.allowedExtensionSet();
        long newBytes = 0L;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(400, "存在空文件，无法上传");
            }
            String ext = fileStorageService.extensionOf(file.getOriginalFilename());
            if (ext.isEmpty() || !whitelist.contains(ext)) {
                throw new BusinessException(400, "不支持的文件类型：" + safeName(file.getOriginalFilename()));
            }
            newBytes += file.getSize();
        }

        String batchId = StringUtils.hasText(uploadBatchId) ? uploadBatchId.trim() : UUID.randomUUID().toString();
        long existingBytes = materialRepository.findByUploadBatchId(batchId).stream()
                .mapToLong(item -> item.getFileSize() == null ? 0L : item.getFileSize())
                .sum();
        long total = existingBytes + newBytes;
        if (total > properties.getMaxFolderBytes()) {
            throw new BusinessException(400, "文件夹累计大小超过上限（"
                    + (properties.getMaxFolderBytes() / (1024 * 1024)) + "MB）");
        }

        List<CampusCourseMaterial> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            String url;
            try {
                url = fileStorageService.store(file, courseId);
            } catch (IOException error) {
                throw new BusinessException(500, "文件保存失败：" + safeName(file.getOriginalFilename()));
            }
            CampusCourseMaterial material = new CampusCourseMaterial();
            material.setCourseId(courseId);
            material.setFileName(safeName(file.getOriginalFilename()));
            material.setFileUrl(url);
            material.setFileSize(file.getSize());
            String ext = fileStorageService.extensionOf(file.getOriginalFilename());
            material.setFileType(ext);
            material.setMimeType(FileStorageService.resolveContentType(ext));
            material.setDurationSeconds(0);
            material.setDeleted(false);
            material.setUploadBatchId(batchId);
            saved.add(materialRepository.save(material));
        }

        MaterialDTO.FolderUploadResult result = new MaterialDTO.FolderUploadResult();
        result.setUploadBatchId(batchId);
        result.setUploadedCount(saved.size());
        result.setUploadedBytes(newBytes);
        result.setBatchTotalBytes(total);
        result.setMaterials(saved.stream().map(this::toView).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public MaterialDTO.ReferenceCheck checkReference(Long materialId) {
        CampusCourseMaterial material = requireMaterial(materialId);
        List<String> titles = referencingChapters(material.getCourseId(), materialId).stream()
                .map(CampusCourseChapter::getTitle)
                .toList();
        MaterialDTO.ReferenceCheck check = new MaterialDTO.ReferenceCheck();
        check.setMaterialId(materialId);
        check.setReferenced(!titles.isEmpty());
        check.setChapterTitles(titles);
        return check;
    }

    /**
     * 删除资料。默认软删除：从引用章节的 material_ids 中剔除该 ID，并置 deleted=true（保留文件）。
     * physical=true 时，在软删除基础上追加物理删除文件（不可逆，需管理员二次确认）。
     */
    @Transactional
    public void delete(Long materialId, boolean physical) {
        CampusCourseMaterial material = requireMaterial(materialId);

        List<CampusCourseChapter> chapters = referencingChapters(material.getCourseId(), materialId);
        for (CampusCourseChapter chapter : chapters) {
            List<Long> ids = materialIdsCodec.parse(chapter.getMaterialIds());
            if (ids.remove(materialId)) {
                chapter.setMaterialIds(materialIdsCodec.write(ids));
                chapterRepository.save(chapter);
            }
        }

        if (!Boolean.TRUE.equals(material.getDeleted())) {
            material.setDeleted(true);
            materialRepository.save(material);
        }

        if (physical) {
            fileStorageService.deletePhysical(material.getFileUrl());
        }
    }

    /** 查询某章节已绑定的资料列表（按 material_ids 存储顺序，过滤已下架/不存在）。 */
    @Transactional(readOnly = true)
    public List<MaterialDTO.MaterialView> getChapterMaterials(Long courseId, Long chapterId) {
        requireCourse(courseId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        List<Long> ids = materialIdsCodec.parse(chapter.getMaterialIds());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, CampusCourseMaterial> map = new HashMap<>();
        materialRepository.findByIdInAndDeletedFalse(ids).forEach(item -> map.put(item.getId(), item));
        List<MaterialDTO.MaterialView> views = new ArrayList<>();
        for (Long id : ids) {
            CampusCourseMaterial material = map.get(id);
            if (material != null) {
                views.add(toView(material));
            }
        }
        return views;
    }

    /**
     * 绑定章节资料：将选中的资料 ID 数组（保留顺序）写入章节 material_ids。
     * 校验章节归属课程、资料归属同一课程且未下架；仅允许视频类型，且最多一个。
     * 允许传空以清空绑定。
     */
    @Transactional
    public List<MaterialDTO.MaterialView> setChapterMaterials(
            Long courseId, Long chapterId, List<Long> materialIds
    ) {
        requireCourse(courseId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);

        List<Long> ordered = new ArrayList<>();
        if (materialIds != null) {
            for (Long id : materialIds) {
                if (id != null && !ordered.contains(id)) {
                    ordered.add(id);
                }
            }
        }
        if (ordered.size() > 1) {
            throw new BusinessException(400, "关联资料池仅允许添加一个视频资料");
        }
        if (!ordered.isEmpty()) {
            Map<Long, CampusCourseMaterial> valid = new HashMap<>();
            materialRepository.findByIdInAndDeletedFalse(ordered)
                    .forEach(item -> valid.put(item.getId(), item));
            for (Long id : ordered) {
                CampusCourseMaterial material = valid.get(id);
                if (material == null || !Objects.equals(material.getCourseId(), courseId)) {
                    throw new BusinessException(400, "资料不存在、已下架或不属于当前课程：" + id);
                }
                String ext = (material.getFileType() == null ? "" : material.getFileType()).toLowerCase();
                if (!VIDEO_EXTENSIONS.contains(ext)) {
                    throw new BusinessException(400, "关联资料池仅允许添加视频类型资料：" + material.getFileName());
                }
            }
        }

        chapter.setMaterialIds(materialIdsCodec.write(ordered));
        chapterRepository.save(chapter);
        return getChapterMaterials(courseId, chapterId);
    }

    /** 查询某章节已绑定的附加资料列表（非视频类型，按 additional_material_ids 存储顺序）。 */
    @Transactional(readOnly = true)
    public List<MaterialDTO.MaterialView> getChapterAdditionalMaterials(Long courseId, Long chapterId) {
        requireCourse(courseId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        List<Long> ids = materialIdsCodec.parse(chapter.getAdditionalMaterialIds());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, CampusCourseMaterial> map = new HashMap<>();
        materialRepository.findByIdInAndDeletedFalse(ids).forEach(item -> map.put(item.getId(), item));
        List<MaterialDTO.MaterialView> views = new ArrayList<>();
        for (Long id : ids) {
            CampusCourseMaterial material = map.get(id);
            if (material != null) {
                views.add(toView(material));
            }
        }
        return views;
    }

    /**
     * 绑定章节附加资料：将选中的资料 ID 数组写入章节 additional_material_ids。
     * 仅允许非视频类型资料（文本/PDF/文档等），可多个。允许传空以清空。
     */
    @Transactional
    public List<MaterialDTO.MaterialView> setChapterAdditionalMaterials(
            Long courseId, Long chapterId, List<Long> materialIds
    ) {
        requireCourse(courseId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);

        List<Long> ordered = new ArrayList<>();
        if (materialIds != null) {
            Set<Long> seen = new LinkedHashSet<>();
            for (Long id : materialIds) {
                if (id != null) {
                    seen.add(id);
                }
            }
            ordered.addAll(seen);
        }
        if (!ordered.isEmpty()) {
            Map<Long, CampusCourseMaterial> valid = new HashMap<>();
            materialRepository.findByIdInAndDeletedFalse(ordered)
                    .forEach(item -> valid.put(item.getId(), item));
            for (Long id : ordered) {
                CampusCourseMaterial material = valid.get(id);
                if (material == null || !Objects.equals(material.getCourseId(), courseId)) {
                    throw new BusinessException(400, "资料不存在、已下架或不属于当前课程：" + id);
                }
                String ext = (material.getFileType() == null ? "" : material.getFileType()).toLowerCase();
                if (VIDEO_EXTENSIONS.contains(ext)) {
                    throw new BusinessException(400, "附加资料不允许视频类型，请使用关联资料池：" + material.getFileName());
                }
            }
        }

        chapter.setAdditionalMaterialIds(materialIdsCodec.write(ordered));
        chapterRepository.save(chapter);
        return getChapterAdditionalMaterials(courseId, chapterId);
    }

    /** 查询某章节已绑定的 Word 文本资料列表（仅 doc/docx）。 */
    @Transactional(readOnly = true)
    public List<MaterialDTO.MaterialView> getChapterWordMaterials(Long courseId, Long chapterId) {
        requireCourse(courseId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);
        List<Long> ids = materialIdsCodec.parse(chapter.getWordMaterialIds());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, CampusCourseMaterial> map = new HashMap<>();
        materialRepository.findByIdInAndDeletedFalse(ids).forEach(item -> map.put(item.getId(), item));
        List<MaterialDTO.MaterialView> views = new ArrayList<>();
        for (Long id : ids) {
            CampusCourseMaterial material = map.get(id);
            if (material != null) {
                views.add(toView(material));
            }
        }
        return views;
    }

    /**
     * 绑定章节 Word 文本资料：将选中的资料 ID 数组写入章节 word_material_ids。
     * 仅允许 Word 类型（doc/docx），可多个。允许传空以清空。
     */
    @Transactional
    public List<MaterialDTO.MaterialView> setChapterWordMaterials(
            Long courseId, Long chapterId, List<Long> materialIds
    ) {
        requireCourse(courseId);
        CampusCourseChapter chapter = requireChapter(courseId, chapterId);

        List<Long> ordered = new ArrayList<>();
        if (materialIds != null) {
            Set<Long> seen = new LinkedHashSet<>();
            for (Long id : materialIds) {
                if (id != null) {
                    seen.add(id);
                }
            }
            ordered.addAll(seen);
        }
        if (!ordered.isEmpty()) {
            Map<Long, CampusCourseMaterial> valid = new HashMap<>();
            materialRepository.findByIdInAndDeletedFalse(ordered)
                    .forEach(item -> valid.put(item.getId(), item));
            for (Long id : ordered) {
                CampusCourseMaterial material = valid.get(id);
                if (material == null || !Objects.equals(material.getCourseId(), courseId)) {
                    throw new BusinessException(400, "资料不存在、已下架或不属于当前课程：" + id);
                }
                String ext = (material.getFileType() == null ? "" : material.getFileType()).toLowerCase();
                if (!WORD_EXTENSIONS.contains(ext)) {
                    throw new BusinessException(400, "关联文本资料仅允许 Word 类型（doc/docx）：" + material.getFileName());
                }
            }
        }

        chapter.setWordMaterialIds(materialIdsCodec.write(ordered));
        chapterRepository.save(chapter);
        return getChapterWordMaterials(courseId, chapterId);
    }

    private CampusCourseChapter requireChapter(Long courseId, Long chapterId) {
        return chapterRepository.findById(chapterId)
                .filter(item -> Objects.equals(item.getCourseId(), courseId))
                .orElseThrow(() -> new BusinessException(404, "课程章节不存在"));
    }

    private List<CampusCourseChapter> referencingChapters(Long courseId, Long materialId) {
        return chapterRepository.findByCourseIdOrderBySortOrderAscIdAsc(courseId).stream()
                .filter(chapter -> materialIdsCodec.contains(chapter.getMaterialIds(), materialId))
                .toList();
    }

    private MaterialDTO.MaterialView toView(CampusCourseMaterial material) {
        MaterialDTO.MaterialView view = new MaterialDTO.MaterialView();
        view.setId(material.getId());
        view.setCourseId(material.getCourseId());
        view.setFileName(material.getFileName());
        view.setFileUrl(material.getFileUrl());
        view.setFileSize(material.getFileSize());
        view.setFileType(material.getFileType());
        view.setMimeType(material.getMimeType());
        view.setDurationSeconds(material.getDurationSeconds());
        view.setDeleted(material.getDeleted());
        view.setUploadBatchId(material.getUploadBatchId());
        view.setCreatedAt(material.getCreatedAt());
        return view;
    }

    private void requireCourse(Long courseId) {
        if (courseId == null || !courseRepository.existsById(courseId)) {
            throw new BusinessException(404, "课程不存在");
        }
    }

    private CampusCourseMaterial requireMaterial(Long materialId) {
        return materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(404, "资料不存在"));
    }

    private String safeName(String original) {
        String cleaned = StringUtils.cleanPath(StringUtils.hasText(original) ? original : "material");
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String name = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
