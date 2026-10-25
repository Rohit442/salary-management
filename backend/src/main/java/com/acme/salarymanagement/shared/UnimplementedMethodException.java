package com.acme.salarymanagement.shared;

public class UnimplementedMethodException extends UnsupportedOperationException {
    public UnimplementedMethodException(String operation) {
        super(operation + " is not implemented");
    }
}
