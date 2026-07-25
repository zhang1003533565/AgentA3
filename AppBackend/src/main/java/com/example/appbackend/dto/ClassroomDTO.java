package com.example.appbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClassroomDTO {
    private Long id;
    private Long buildingId;

    @NotBlank(message = "教室编号不能为空")
    private String roomNo;

    @NotNull(message = "楼层不能为空")
    @Min(value = 1, message = "楼层必须大于 0")
    private Integer floorNo;

    @NotNull(message = "座位数不能为空")
    @Min(value = 0, message = "座位数不能小于 0")
    private Integer seatCount;

    private Boolean smart;
    private Integer status;
    private String openTime;
}
