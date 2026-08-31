package com.lms.netcafe.common.api;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, traceId());
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, traceId());
    }

    private static String traceId() {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(OffsetDateTime.now());
    }
}
