package com.example.ragkb.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {                    
        return httpStatus;
    }
}
