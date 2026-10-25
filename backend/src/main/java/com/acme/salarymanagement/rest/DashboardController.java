package com.acme.salarymanagement.rest;

import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.bean.CompensationReport;
import com.acme.salarymanagement.bean.ReferenceDataResponse;
import com.acme.salarymanagement.exception.ApiOperationException;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.service.AuthService;
import com.acme.salarymanagement.service.DashboardService;
import com.acme.salarymanagement.util.SessionUtility;
import com.acme.salarymanagement.validator.DashboardRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final DashboardRequestValidator validator;
    private final DashboardService service;
    private final AuthService auth;
    private final SessionUtility sessions;
    public DashboardController(DashboardRequestValidator validator, DashboardService service,
            AuthService auth, SessionUtility sessions) {
        this.validator = validator; this.service = service; this.auth = auth; this.sessions = sessions;
    }
    @GetMapping(value = "/api/reports/compensation", produces = "application/json")
    public ResponseEntity<CompensationReport> compensation(HttpServletRequest request, Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request, user);
            errors = validator.validate(request, service.referenceData(), true);
            if (errors.isEmpty()) {
                CompensationReport response = service.compensation(validator.filters(request), validator.grouping(request));
                return ResponseEntity.ok(response);
            }
            throw invalid(errors);
        } catch (ApiRequestException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    @GetMapping(value = "/api/reference-data", produces = "application/json")
    public ResponseEntity<ReferenceDataResponse> referenceData(HttpServletRequest request, Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request, user);
            errors = validator.validate(request, null, false);
            if (errors.isEmpty()) {
                ReferenceDataResponse response = service.referenceData();
                return ResponseEntity.ok(response);
            }
            throw invalid(errors);
        } catch (ApiRequestException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    private void authorize(HttpServletRequest request, Authentication user) {
        try { auth.currentUser(user.getName(), sessions.idleTimeoutSeconds(request)); }
        catch (ApiRequestException exception) {
            if (exception.status() == HttpStatus.UNAUTHORIZED || exception.status() == HttpStatus.FORBIDDEN) sessions.invalidate(request);
            throw exception;
        }
    }
    private ApiRequestException invalid(List<ApiFieldError> errors) {
        return new ApiRequestException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the report filters and grouping.", errors);
    }
}
