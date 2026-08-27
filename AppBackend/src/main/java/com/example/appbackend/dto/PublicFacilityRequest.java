package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "公共设施请求")
public class PublicFacilityRequest {

    @NotBlank(message = "设施名称不能为空")
    @Size(max = 100, message = "设施名称长度不能超过100")
    @Schema(description = "设施名称", example = "东门自行车停放点")
    private String name;

    @NotBlank(message = "设施类型不能为空")
    @Size(max = 32, message = "设施类型长度不能超过32")
    @Schema(description = "设施类型", example = "BICYCLE_RACK")
    private String type;

    @Size(max = 200, message = "位置描述长度不能超过200")
    @Schema(description = "位置描述", example = "东门入口左侧")
    private String location;

    @Schema(description = "详细描述", example = "可容纳20辆自行车，24小时开放")
    private String description;

    @Size(max = 16, message = "状态长度不能超过16")
    @Schema(description = "状态: ACTIVE-正常 MAINTENANCE-维护中 INACTIVE-停用", example = "ACTIVE")
    private String status;

    @Schema(description = "纬度", example = "39.90923")
    private BigDecimal latitude;

    @Schema(description = "经度", example = "116.397428")
    private BigDecimal longitude;

    @Schema(description = "距离，单位米", example = "150")
    private Integer distance;

    @Size(max = 500, message = "图片URL长度不能超过500")
    @Schema(description = "图片URL", example = "https://example.com/facility.jpg")
    private String imageUrl;
}
