package com.travelmate.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常。携带 HTTP 状态与业务错误码。
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final int code;

    public ApiException(HttpStatus status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, 40000, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, 40100, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, 40300, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, 40400, message);
    }

    public static ApiException serviceUnavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, 50300, message);
    }
}
