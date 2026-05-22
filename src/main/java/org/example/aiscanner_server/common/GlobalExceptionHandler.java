package org.example.aiscanner_server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ApiResponse.error(400, "视频文件过大，最大支持 10MB");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResponse.error(400, "缺少必要参数: " + e.getParameterName());
    }

    // 3. 非 multipart 请求（例如没有上传视频文件）
    @ExceptionHandler(MultipartException.class)
    public ApiResponse<Void> handleMultipartException(MultipartException e) {
        return ApiResponse.error(400, "请求必须包含视频文件");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("未捕获异常", e);
        return ApiResponse.error(500, "服务器内部错误");
    }
}
