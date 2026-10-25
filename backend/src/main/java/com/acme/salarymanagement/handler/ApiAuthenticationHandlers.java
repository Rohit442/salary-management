package com.acme.salarymanagement.handler;

import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.exception.NonHrAuthenticationException;
import com.acme.salarymanagement.service.AuthService;
import com.acme.salarymanagement.service.LoginAttemptLimiter;
import com.acme.salarymanagement.util.ApiResponseWriter;
import com.acme.salarymanagement.util.SessionUtility;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public class ApiAuthenticationHandlers {
    private final AuthService service;
    private final LoginAttemptLimiter attempts;
    private final ApiResponseWriter responses;
    private final SessionUtility sessions;

    public ApiAuthenticationHandlers(AuthService service, LoginAttemptLimiter attempts,
            ApiResponseWriter responses, SessionUtility sessions) {
        this.service = service;
        this.attempts = attempts;
        this.responses = responses;
        this.sessions = sessions;
    }

    public void success(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        try {
            var result = service.currentUser(authentication.getName(), sessions.idleTimeoutSeconds(request));
            attempts.recordSuccess(request.getParameter("email"), request.getRemoteAddr());
            responses.json(request, response, 200, result);
        } catch (ApiRequestException exception) {
            sessions.invalidate(request);
            sessions.expireCookie(request, response);
            responses.failure(request, response, exception);
        }
    }

    public void failure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        if (exception instanceof NonHrAuthenticationException) {
            responses.error(request, response, 403, "ACCESS_DENIED", "You do not have access to this HR workspace.", List.of());
        } else if (exception instanceof AuthenticationServiceException) {
            // Spring's authentication filter logs internal service failures; do not log twice.
            responses.error(request, response, 503, "SERVICE_UNAVAILABLE", "Authentication is temporarily unavailable.", List.of());
        } else {
            attempts.recordFailure(request.getParameter("email"), request.getRemoteAddr());
            responses.error(request, response, 401, "INVALID_CREDENTIALS", "Email or password is incorrect.", List.of());
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        responses.prepare(request, response);
        response.setStatus(204);
        response.setContentType(null);
    }
}
