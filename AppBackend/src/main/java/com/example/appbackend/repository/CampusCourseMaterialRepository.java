package com.example.appbackend.repository;

import com.example.appbackend.entity.CampusCourseMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CampusCourseMaterialRepository extends JpaRepository<CampusCourseMaterial, Long> {

    /** 资料池：某课程下未下架的资料，按创建时间倒序。 */
    List<CampusCourseMaterial> findByCourseIdAndDeletedFalseOrderByIdDesc(Long courseId);

    /** 资料池（含已下架），管理端可选查看。 */
    List<CampusCourseMaterial> findByCourseIdOrderByIdDesc(Long courseId);

    /** 按 ID 批量查询未下架资料，用于解析章节引用列表。 */
    List<CampusCourseMaterial> findByIdInAndDeletedFalse(Collection<Long> ids);

    /** 同批次已上传文件的字节总和（跨请求累计校验用）。 */
    List<CampusCourseMaterial> findByUploadBatchId(String uploadBatchId);
}
