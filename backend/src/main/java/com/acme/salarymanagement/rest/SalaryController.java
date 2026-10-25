package com.acme.salarymanagement.rest;

import com.acme.salarymanagement.bean.*;
import com.acme.salarymanagement.exception.ApiOperationException;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.service.AuthService;
import com.acme.salarymanagement.service.SalaryService;
import com.acme.salarymanagement.util.JsonRequestUtility;
import com.acme.salarymanagement.util.SessionUtility;
import com.acme.salarymanagement.validator.EmployeeWriteValidator;
import com.acme.salarymanagement.validator.SalaryRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{employeeId}")
public class SalaryController {
    private final SalaryService service;
    private final AuthService auth;
    private final SessionUtility sessions;
    private final JsonRequestUtility bodies;
    private final EmployeeWriteValidator writes;
    private final SalaryRequestValidator validator;
    public SalaryController(SalaryService service,AuthService auth,SessionUtility sessions,JsonRequestUtility bodies,
            EmployeeWriteValidator writes,SalaryRequestValidator validator) {
        this.service=service;this.auth=auth;this.sessions=sessions;this.bodies=bodies;this.writes=writes;this.validator=validator;
    }
    @PostMapping(value="/salary-changes",produces="application/json")
    public ResponseEntity<SalaryChangeResponse> change(@PathVariable String employeeId,HttpServletRequest request,Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request,user);
            var node=bodies.read(request,16384);
            errors=new ArrayList<>(writes.validate(request,node,"salary",employeeId,null));
            errors.addAll(validator.validateKey(request));
            if(errors.isEmpty()) {
                SalaryChangeResult response=service.change(employeeId,bodies.convert(node,SalaryChangeRequest.class),request.getHeader("Idempotency-Key"),user.getName());
                return ResponseEntity.status(201).header("Idempotency-Replayed",Boolean.toString(response.replayed())).body(response.change());
            }
            throw invalid(errors);
        } catch(ApiRequestException exception) { throw exception; }
        catch(IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    @GetMapping(value="/salary-changes",produces="application/json")
    public ResponseEntity<SalaryHistoryPage> history(@PathVariable String employeeId,HttpServletRequest request,Authentication user) {
        List<ApiFieldError> errors;
        try {
            authorize(request,user);
            errors=validator.validateHistory(request,employeeId);
            if(errors.isEmpty()) {
                SalaryHistoryPage response=service.history(employeeId,validator.page(request),validator.size(request));
                return ResponseEntity.ok(response);
            }
            throw invalid(errors);
        } catch(ApiRequestException exception) { throw exception; }
        catch(IOException | RuntimeException exception) { throw new ApiOperationException(exception); }
    }
    private void authorize(HttpServletRequest request,Authentication user) {
        try { auth.currentUser(user.getName(),sessions.idleTimeoutSeconds(request)); }
        catch(ApiRequestException exception) {
            if(exception.status()==HttpStatus.UNAUTHORIZED || exception.status()==HttpStatus.FORBIDDEN) sessions.invalidate(request);
            throw exception;
        }
    }
    private ApiRequestException invalid(List<ApiFieldError> errors) { return new ApiRequestException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Check the salary request.",errors); }
}
