package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "二手物品举报创建请求")
public class SecondhandReportCreateRequest {

    @NotNull(message = "被举报物品ID不能为空")
    @Schema(description = "被举报物品ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long itemId;

    @NotBlank(message = "举报人姓名不能为空")
    @Size(max = 50, message = "举报人姓名不能超过50个字符")
    @Schema(description = "举报人姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String reporterName;

    @NotBlank(message = "举报人联系方式不能为空")
    @Size(max = 100, message = "举报人联系方式不能超过100个字符")
    @Schema(description = "举报人联系方式", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    private String reporterContact;

    @Schema(description = "举报原因类型：1-虚假信息 2-不良行为 3-其他违规", example = "1")
    private Integer reasonType;

    @NotBlank(message = "举报理由不能为空")
    @Size(max = 2000, message = "举报理由不能超过2000个字符")
    @Schema(description = "举报详细理由", requiredMode = Schema.RequiredMode.REQUIRED, example = "商品描述与实物不符，存在欺诈行为")
    private String reason;
}
