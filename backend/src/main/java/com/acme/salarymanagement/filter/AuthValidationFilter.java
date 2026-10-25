package com.acme.salarymanagement.filter;

import com.acme.salarymanagement.bean.ValidatedFormRequest;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.service.LoginAttemptLimiter;
import com.acme.salarymanagement.util.ApiResponseWriter;
import com.acme.salarymanagement.validator.AuthRequestValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after CSRF, but before Spring login/logout changes any session state. */
public class AuthValidationFilter extends OncePerRequestFilter {
    private final AuthRequestValidator validator;
    private final LoginAttemptLimiter attempts;
    private final ApiResponseWriter responses;

    public AuthValidationFilter(AuthRequestValidator validator, LoginAttemptLimiter attempts, ApiResponseWriter responses) {
        this.validator = validator;
        this.attempts = attempts;
        this.responses = responses;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            validator.validateProtocol(request, path);
            if (path.equals("/api/auth/login")) {
                var fields = validator.readLoginForm(request);
                var errors = validator.validateLoginFields(fields);
                if (!errors.isEmpty()) throw new ApiRequestException(HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR", "Check the required sign-in fields.", errors);
                var validated = new ValidatedFormRequest(request, fields);
                long retryAfter = attempts.retryAfterSeconds(validated.getParameter("email"), request.getRemoteAddr());
                if (retryAfter > 0) {
                    response.setHeader("Retry-After", Long.toString(retryAfter));
                    responses.error(request, response, 429, "TOO_MANY_ATTEMPTS",
                            "Too many sign-in attempts. Please try again later.", List.of());
                    return;
                }
                chain.doFilter(validated, response);
            } else {
                if (path.equals("/api/auth/logout")) {
                    var errors = validator.validateBodylessRequest(request);
                    if (!errors.isEmpty()) throw new ApiRequestException(HttpStatus.BAD_REQUEST,
                            "VALIDATION_ERROR", "Check the request parameters.", errors);
                }
                chain.doFilter(request, response);
            }
        } catch (ApiRequestException exception) {
            if (exception.status() == HttpStatus.METHOD_NOT_ALLOWED) {
                response.setHeader("Allow", validator.allowedMethod(request));
            }
            responses.failure(request, response, exception);
        }
    }
}
