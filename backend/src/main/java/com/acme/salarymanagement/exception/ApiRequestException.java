package com.acme.salarymanagement.exception;

import com.acme.salarymanagement.bean.ApiFieldError;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiRequestException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<ApiFieldError> fieldErrors;

    public ApiRequestException(HttpStatus status, String code, String message, List<ApiFieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ApiRequestException(HttpStatus status, String code, String message, List<ApiFieldError> errors, Throwable cause) {
        this(status, code, message, errors);
        initCause(cause);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public List<ApiFieldError> fieldErrors() { return fieldErrors; }
}
