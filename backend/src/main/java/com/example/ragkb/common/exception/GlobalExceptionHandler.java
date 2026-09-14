package com.example.ragkb.common.exception;

import com.example.ragkb.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(ApiResponse.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        return validationResponse(exception.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(
            BindException exception) {
        return validationResponse(exception.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()));
        return errorResponse(ErrorCode.INVALID_ARGUMENT, errors);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRequestParameterException(Exception exception) {
        return errorResponse(ErrorCode.INVALID_ARGUMENT, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity
                .status(ErrorCode.INVALID_ARGUMENT.getHttpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_ARGUMENT.getCode(),
                        "上传文件不能超过 10MB"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException exception) {
        return errorResponse(ErrorCode.MALFORMED_REQUEST, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(
                        ErrorCode.INVALID_ARGUMENT.getCode(),
                        "请求方法不支持"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            NoResourceFoundException exception) {
        return errorResponse(ErrorCode.RESOURCE_NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unexpected server error", exception);
        return errorResponse(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Map<String, String>>> validationResponse(
            Iterable<FieldError> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        fieldErrors.forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return errorResponse(ErrorCode.INVALID_ARGUMENT, errors);
    }

    private <T> ResponseEntity<ApiResponse<T>> errorResponse(ErrorCode errorCode, T data) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        data));
    }
}
