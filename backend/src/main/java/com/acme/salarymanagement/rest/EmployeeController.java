package com.acme.salarymanagement.rest;

import com.acme.salarymanagement.bean.CreateEmployeeRequest;
import com.acme.salarymanagement.bean.EmployeeDetailsRequest;
import com.acme.salarymanagement.bean.EmployeeStatusRequest;
import com.acme.salarymanagement.util.JsonRequestUtility;
import com.acme.salarymanagement.validator.EmployeeWriteValidator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.bean.EmployeePage;
import com.acme.salarymanagement.bean.EmployeeResponse;
import com.acme.salarymanagement.exception.ApiOperationException;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.service.AuthService;
import com.acme.salarymanagement.service.DashboardService;
import com.acme.salarymanagement.service.EmployeeService;
import com.acme.salarymanagement.util.SessionUtility;
import com.acme.salarymanagement.validator.EmployeeRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private final EmployeeService service;
    private final EmployeeRequestValidator validator;
    private final DashboardService references;
    private final AuthService auth;
    private final SessionUtility sessions;
    private final JsonRequestUtility bodies;
    private final EmployeeWriteValidator writeValidator;
    public EmployeeController(EmployeeService service, EmployeeRequestValidator validator,
            DashboardService references, AuthService auth, SessionUtility sessions,
            JsonRequestUtility bodies,EmployeeWriteValidator writeValidator) {
        this.service = service; this.validator = validator; this.references = references; this.auth = auth; this.sessions = sessions; this.bodies=bodies; this.writeValidator=writeValidator;
    }
    @GetMapping(produces = "application/json")
    public ResponseEntity<EmployeePage> list(HttpServletRequest request, Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request, user);
            errors = validator.validateList(request, references.referenceData());
            if (errors.isEmpty()) {
                EmployeePage response = service.list(validator.query(request));
                return ResponseEntity.ok(response);
            }
            throw invalid(errors);
        } catch (ApiRequestException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    @GetMapping(value = "/{employeeId}", produces = "application/json")
    public ResponseEntity<EmployeeResponse> details(@PathVariable String employeeId, HttpServletRequest request, Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request, user);
            errors = validator.validateDetails(request, employeeId);
            if (errors.isEmpty()) {
                EmployeeResponse response = service.details(employeeId);
                return ResponseEntity.ok(response);
            }
            throw invalid(errors);
        } catch (ApiRequestException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    @PostMapping(value="",produces="application/json")
    public ResponseEntity<EmployeeResponse> create(HttpServletRequest request,Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request,user);
            var node=bodies.read(request,16384);
            errors=writeValidator.validate(request,node,"create",null,references.referenceData());
            if (errors.isEmpty()) {
                var body=bodies.convert(node,CreateEmployeeRequest.class);
                EmployeeResponse response=service.create(body,user.getName());
                return ResponseEntity.created(java.net.URI.create("/api/employees/"+response.employeeId())).body(response);
            }
            throw invalid(errors);
        } catch(ApiRequestException exception) { throw exception; }
        catch(IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    @PutMapping(value="/{employeeId}/details",produces="application/json")
    public ResponseEntity<EmployeeResponse> updateDetails(@PathVariable String employeeId, HttpServletRequest request,Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request,user);
            var node=bodies.read(request,16384);
            errors=writeValidator.validate(request,node,"details",employeeId,references.referenceData());
            if (errors.isEmpty()) {
                var body=bodies.convert(node,EmployeeDetailsRequest.class);
                EmployeeResponse response=service.updateDetails(employeeId,body);
                return ResponseEntity.ok(response);
            }
            throw invalid(errors);
        } catch(ApiRequestException exception) { throw exception; }
        catch(IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    @PutMapping(value="/{employeeId}/status",produces="application/json")
    public ResponseEntity<EmployeeResponse> inactivate(@PathVariable String employeeId, HttpServletRequest request,Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request,user);
            var node=bodies.read(request,16384);
            errors=writeValidator.validate(request,node,"status",employeeId,null);
            if (errors.isEmpty()) {
                var body=bodies.convert(node,EmployeeStatusRequest.class);
                EmployeeResponse response=service.inactivate(employeeId,body.version());
                return ResponseEntity.ok(response);
            }
            throw invalid(errors);
        } catch(ApiRequestException exception) { throw exception; }
        catch(IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    private void authorize(HttpServletRequest request, Authentication user) {
        try { auth.currentUser(user.getName(), sessions.idleTimeoutSeconds(request)); }
        catch (ApiRequestException exception) {
            if (exception.status() == HttpStatus.UNAUTHORIZED || exception.status() == HttpStatus.FORBIDDEN) sessions.invalidate(request);
            throw exception;
        }
    }
    private ApiRequestException invalid(List<ApiFieldError> errors) {
        return new ApiRequestException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the employee request parameters.", errors);
    }
}
