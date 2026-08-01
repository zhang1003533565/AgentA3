package com.example.appbackend.controller;

import com.example.appbackend.dto.MaterialDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CourseMaterialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课程资料池管理接口（管理端，仅新增）。
 * 通过 JwtInterceptor 已完成鉴权，这里再校验 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/admin/materials")
public class AdminMaterialController {

    private final CourseMaterialService materialService;

    public AdminMaterialController(CourseMaterialService materialService) {
        this.materialService = materialService;
    }

    /** 资料池列表：默认仅返回未下架资料，includeDeleted=true 时含已下架。 */
    @GetMapping
    public Result<?> list(@RequestParam Long courseId,
                          @RequestParam(defaultValue = "false") boolean includeDeleted,
                          HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(materialService.listMaterials(courseId, includeDeleted));
    }

    /** 文件夹上传（前端平铺传递文件夹内文件，可分批携带同一 uploadBatchId 跨请求累计校验）。 */
    @PostMapping(value = "/folder/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> folderUpload(@RequestParam Long courseId,
                                  @RequestParam("files") List<MultipartFile> files,
                                  @RequestParam(value = "uploadBatchId", required = false) String uploadBatchId,
                                  HttpServletRequest request) {
        requireAdmin(request);
        MaterialDTO.FolderUploadResult result = materialService.folderUpload(courseId, files, uploadBatchId);
        return Result.success("资料上传成功", result);
    }

    /** 引用检查：返回引用该资料的章节名称列表。 */
    @GetMapping("/{id}/check")
    public Result<?> check(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(materialService.checkReference(id));
    }

    /** 删除资料：默认软删除；physical=true 时追加物理删除文件（不可逆）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean physical,
                               HttpServletRequest request) {
        requireAdmin(request);
        materialService.delete(id, physical);
        return Result.success(physical ? "资料已删除（含文件）" : "资料已下架", null);
    }

    /** 查询某章节已绑定的资料列表（按存储顺序）。 */
    @GetMapping("/chapter/{chapterId}")
    public Result<?> chapterMaterials(@PathVariable Long chapterId,
                                      @RequestParam Long courseId,
                                      HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(materialService.getChapterMaterials(courseId, chapterId));
    }

    /** 绑定章节资料：将选中资料 ID 数组（保留顺序）写入章节 material_ids。 */
    @PutMapping("/chapter/{chapterId}")
    public Result<?> bindChapterMaterials(@PathVariable Long chapterId,
                                          @Valid @RequestBody MaterialDTO.ChapterBindRequest body,
                                          HttpServletRequest request) {
        requireAdmin(request);
        return Result.success("章节资料已保存",
                materialService.setChapterMaterials(body.getCourseId(), chapterId, body.getMaterialIds()));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!"ADMIN".equals(request.getAttribute("role"))) {
            throw new BusinessException(403, "仅管理员可管理课程资料");
        }
        if (!(request.getAttribute("userId") instanceof Long)) {
            throw new BusinessException(401, "请先登录");
        }
    }
}
