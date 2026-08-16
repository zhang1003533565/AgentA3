package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "课表导入变更摘要")
public class ScheduleChangeSummary {

    @Schema(description = "导入前课程数量")
    private Integer oldCount = 0;

    @Schema(description = "导入后课程数量")
    private Integer newCount = 0;

    @Schema(description = "无变化课程数量")
    private Integer unchangedCount = 0;

    @Schema(description = "新增课程数量")
    private Integer addedCount = 0;

    @Schema(description = "删除课程数量")
    private Integer removedCount = 0;

    @Schema(description = "变更课程数量")
    private Integer updatedCount = 0;

    @Schema(description = "是否存在变更")
    private Boolean hasChanges = false;

    @Schema(description = "新增课程")
    private List<CourseChangeItem> added = new ArrayList<>();

    @Schema(description = "删除课程")
    private List<CourseChangeItem> removed = new ArrayList<>();

    @Schema(description = "变更课程")
    private List<CourseChangeItem> updated = new ArrayList<>();

    public static ScheduleChangeSummary of(
            int oldCount,
            int newCount,
            int unchangedCount,
            List<CourseChangeItem> added,
            List<CourseChangeItem> removed,
            List<CourseChangeItem> updated) {
        ScheduleChangeSummary summary = new ScheduleChangeSummary();
        summary.setOldCount(oldCount);
        summary.setNewCount(newCount);
        summary.setUnchangedCount(unchangedCount);
        summary.setAdded(added);
        summary.setRemoved(removed);
        summary.setUpdated(updated);
        summary.setAddedCount(added.size());
        summary.setRemovedCount(removed.size());
        summary.setUpdatedCount(updated.size());
        summary.setHasChanges(!added.isEmpty() || !removed.isEmpty() || !updated.isEmpty());
        return summary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "单门课程变更")
    public static class CourseChangeItem {

        @Schema(description = "课程名称")
        private String courseName;

        @Schema(description = "教学班号")
        private String classCode;

        @Schema(description = "摘要")
        private String summary;

        @Schema(description = "变化字段")
        private List<String> changedFields = new ArrayList<>();
    }
}
