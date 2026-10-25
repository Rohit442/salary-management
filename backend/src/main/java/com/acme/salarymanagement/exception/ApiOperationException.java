package com.acme.salarymanagement.exception;

public class ApiOperationException extends RuntimeException {
    public ApiOperationException(Throwable cause) {
        super("Authentication API operation failed", cause);
    }
}
