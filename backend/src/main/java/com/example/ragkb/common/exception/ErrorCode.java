package com.example.ragkb.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_ARGUMENT(40000, HttpStatus.BAD_REQUEST, "请求参数错误"),
    MALFORMED_REQUEST(40001, HttpStatus.BAD_REQUEST, "请求体格式错误"),
    BUSINESS_ERROR(40002, HttpStatus.BAD_REQUEST, "业务处理失败"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权限访问"),
    RESOURCE_NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
    RESOURCE_CONFLICT(40900, HttpStatus.CONFLICT, "资源冲突"),
    RATE_LIMITED(42900, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
