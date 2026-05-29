package com.interstellar.shorturl.support.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        String error,
        String timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now().toString());
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now().toString());
    }
}
