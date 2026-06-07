package com.example.appbackend.exception;

import com.example.appbackend.entity.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletResponse response) {
        log.warn("业务异常: {}", e.getMessage());
        response.setStatus(e.getCode());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DateTimeParseException.class)
    public Result<?> handleDateTimeParseException(DateTimeParseException e, HttpServletResponse response) {
        log.warn("日期时间解析失败: {}", e.getMessage());
        response.setStatus(Result.BAD_REQUEST_CODE);
        return Result.error(Result.BAD_REQUEST_CODE, "时间格式须为 yyyy-MM-dd HH:mm:ss");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletResponse response) {
        log.warn("上传文件超限: {}", e.getMessage());
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        return Result.error(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "上传文件过大");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e, HttpServletResponse response) {
        log.error("RuntimeException", e);
        response.setStatus(Result.ERROR_CODE);
        return Result.error(e.getMessage() != null ? e.getMessage() : "服务器内部错误");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e, HttpServletResponse response) {
        response.setStatus(Result.BAD_REQUEST_CODE);
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e, HttpServletResponse response) {
        String first = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数验证失败");
        response.setStatus(Result.BAD_REQUEST_CODE);
        return Result.error(Result.BAD_REQUEST_CODE, first);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletResponse response) {
        log.error("未处理异常", e);
        String message = e.getMessage() != null ? e.getMessage() : "服务器内部错误";
        response.setStatus(Result.ERROR_CODE);
        return Result.error(message);
    }
}
