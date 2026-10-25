package com.acme.salarymanagement.service;

import com.acme.salarymanagement.bean.CsrfResponse;
import com.acme.salarymanagement.bean.AuthResponse;

import com.acme.salarymanagement.exception.UnimplementedMethodException;
import org.springframework.security.web.csrf.CsrfToken;

public interface AuthService {
    default CsrfResponse issueCsrfToken(CsrfToken token) {
        throw new UnimplementedMethodException("issueCsrfToken");
    }
    default AuthResponse currentUser(String email, int idleTimeoutSeconds) {
        throw new UnimplementedMethodException("currentUser");
    }
}
