package com.acme.salarymanagement.rest;

import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.bean.AuthResponse;
import com.acme.salarymanagement.util.SessionUtility;
import org.springframework.security.core.Authentication;
import com.acme.salarymanagement.bean.CsrfResponse;
import com.acme.salarymanagement.exception.ApiOperationException;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.service.AuthService;
import com.acme.salarymanagement.validator.AuthRequestValidator;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthRequestValidator validator;
    private final AuthService service;
    private final SessionUtility sessions;

    public AuthController(AuthRequestValidator validator, AuthService service, SessionUtility sessions) {
        this.validator = validator;
        this.service = service;
        this.sessions = sessions;
    }

    @GetMapping(value = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CsrfResponse> csrf(HttpServletRequest request, CsrfToken token) {
        List<ApiFieldError> errors;
        try {
            errors = validator.validateBodylessRequest(request);
            if (errors.isEmpty()) {
                CsrfResponse response = service.issueCsrfToken(token);
                return ResponseEntity.ok(response);
            }
            throw new ApiRequestException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Check the request parameters.", errors);
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiOperationException(exception);
        }
    }
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> me(HttpServletRequest request, Authentication authentication) {
        List<ApiFieldError> errors;
        try {
            errors = validator.validateBodylessRequest(request);
            if (errors.isEmpty()) {
                AuthResponse response = service.currentUser(authentication.getName(), sessions.idleTimeoutSeconds(request));
                return ResponseEntity.ok(response);
            }
            throw new ApiRequestException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the request parameters.", errors);
        } catch (ApiRequestException exception) {
            if (exception.status() == HttpStatus.UNAUTHORIZED || exception.status() == HttpStatus.FORBIDDEN) {
                sessions.invalidate(request);
            }
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiOperationException(exception);
        }
    }

}
