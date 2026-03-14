package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "分页响应")
public class PageResponse<T> {

    @Schema(description = "数据列表")
    private List<T> records;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "当前页码")
    private Integer page;

    @Schema(description = "每页数量")
    private Integer size;
}
