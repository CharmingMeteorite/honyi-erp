package com.haucheng.erp.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(0, "success", data); }
    public static ApiResponse<Void> success() { return new ApiResponse<>(0, "success", null); }
    public static ApiResponse<Void> error(int code, String message) { return new ApiResponse<>(code, message, null); }
}
