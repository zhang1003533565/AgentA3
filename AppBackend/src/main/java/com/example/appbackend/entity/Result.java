package com.example.appbackend.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应结果")
public class Result<T> {

    @Schema(description = "状态码", example = "200")
    private Integer code;

    @Schema(description = "消息", example = "success")
    private String msg;

    @Schema(description = "数据")
    private T data;

    public static final int SUCCESS_CODE = 200;
    public static final int ERROR_CODE = 500;
    public static final int BAD_REQUEST_CODE = 400;
    public static final int UNAUTHORIZED_CODE = 401;
    public static final int FORBIDDEN_CODE = 403;
    public static final int NOT_FOUND_CODE = 404;

    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(SUCCESS_CODE, msg, data);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(ERROR_CODE, msg, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> badRequest(String msg) {
        return new Result<>(BAD_REQUEST_CODE, msg, null);
    }

    public static <T> Result<T> unauthorized(String msg) {
        return new Result<>(UNAUTHORIZED_CODE, msg, null);
    }

    public static <T> Result<T> forbidden(String msg) {
        return new Result<>(FORBIDDEN_CODE, msg, null);
    }

    public static <T> Result<T> notFound(String msg) {
        return new Result<>(NOT_FOUND_CODE, msg, null);
    }
}
