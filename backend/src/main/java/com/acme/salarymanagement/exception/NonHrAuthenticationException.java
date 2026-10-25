package com.acme.salarymanagement.exception;
import org.springframework.security.core.AuthenticationException;
public class NonHrAuthenticationException extends AuthenticationException {
    public NonHrAuthenticationException() { super("HR access is required"); }
}
