package com.acme.salarymanagement.exception;

public class UnimplementedMethodException extends UnsupportedOperationException {
    public UnimplementedMethodException(String operation) {
        super(operation + " is not implemented");
    }
}
