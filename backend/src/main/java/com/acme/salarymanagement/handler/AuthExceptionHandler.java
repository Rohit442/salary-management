package com.acme.salarymanagement.handler;

import com.acme.salarymanagement.exception.ApiOperationException;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.util.ApiResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {
    private final ApiResponseWriter responses;

    public AuthExceptionHandler(ApiResponseWriter responses) {
        this.responses = responses;
    }

    @ExceptionHandler(ApiRequestException.class)
    public void invalidRequest(ApiRequestException exception, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        responses.failure(request, response, exception);
    }

    @ExceptionHandler(ApiOperationException.class)
    public void unexpected(ApiOperationException exception, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        responses.unexpected(request, response, exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public void methodNotAllowed(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!isAuthApi(request)) {
            throw exception;
        }
        if (exception.getSupportedHttpMethods() != null) {
            response.setHeader(HttpHeaders.ALLOW, exception.getSupportedHttpMethods().stream()
                    .map(HttpMethod::name).sorted().collect(Collectors.joining(", ")));
        }
        responses.error(request, response, 405, "METHOD_NOT_ALLOWED", "This HTTP method is not supported.", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public void unacceptableResponse(HttpMediaTypeNotAcceptableException exception,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!isAuthApi(request)) {
            throw exception;
        }
        responses.error(request, response, 406, "NOT_ACCEPTABLE", "This endpoint returns JSON.", List.of());
    }

    private boolean isAuthApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/");
    }
}
