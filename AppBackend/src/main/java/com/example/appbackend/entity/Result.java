package com.example.appbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;

    // 成功状态码
    public static final int SUCCESS_CODE = 200;
    // 错误状态码
    public static final int ERROR_CODE = 500;
    // 参数错误
    public static final int BAD_REQUEST_CODE = 400;
    // 未授权
    public static final int UNAUTHORIZED_CODE = 401;
    // 禁止访问
    public static final int FORBIDDEN_CODE = 403;
    // 资源不存在
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
