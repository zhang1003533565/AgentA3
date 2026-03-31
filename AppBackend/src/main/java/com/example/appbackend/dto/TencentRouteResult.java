package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 腾讯地图路线规划 WebService 响应
 * 文档：https://lbs.qq.com/service/webService/webServiceGuide/route/webServiceRoute
 *
 * 路线等级字段（driving/walking/bicycling/ebicycling 均适用）：
 *   distance  单位：米
 *   duration  单位：分钟
 *   polyline  压缩坐标数字数组 → 使用 coors[i]=coors[i-2]+coors[i]/1e6 解压
 *
 * 步骤等级字段：
 *   polyline_idx  [起始下标, 终止下标]，指在方案 polyline 数组中的下标范围
 *   road_name     路名（非必有）
 *   dir_desc      方向描述
 *   act_desc      末尾动作
 *   type          步行设施类型（仅 walking）：0普通道路 1天桥 2地下通道 3人行横道
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TencentRouteResult {

    private Integer status;
    private String message;

    @JsonProperty("request_id")
    private String requestId;

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private List<TencentRoute> routes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TencentRoute {
        private String mode;
        /** 方案总距离，单位：米 */
        private Integer distance;
        /** 方案估算时间，单位：分钟 */
        private Integer duration;
        /** 方案整体方向（walking/bicycling/ebicycling 有） */
        private String direction;
        /**
         * 方案路线压缩坐标数组。
         * 解压规则：从第 3 项起，coors[i] = coors[i-2] + coors[i] / 1e6，
         * 结果两两一组为 [纬度, 经度]。
         */
        private List<Double> polyline;
        private List<TencentStep> steps;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TencentStep {
        /** 交通方式（仅 transit 有）：WALKING | TRANSIT */
        private String mode;
        /** 阶段路线描述 */
        private String instruction;
        /**
         * 本阶段在方案 polyline 数组中的下标范围 [起始下标, 终止下标]（含）。
         * 注意：下标指压缩数组位置，不是坐标点对下标。
         */
        @JsonProperty("polyline_idx")
        private List<Integer> polylineIdx;
        /** 路名（非必有） */
        @JsonProperty("road_name")
        private String roadName;
        /** 方向描述 */
        @JsonProperty("dir_desc")
        private String dirDesc;
        /** 阶段路线距离，单位：米 */
        private Integer distance;
        /** 阶段估算时间，单位：分钟 */
        private Integer duration;
        /** 末尾动作（如：左转、直行） */
        @JsonProperty("act_desc")
        private String actDesc;
        /**
         * 步行设施类型（仅 walking）：
         *   0 普通道路  1 过街天桥  2 地下通道  3 人行横道
         */
        private Integer type;
    }
}
