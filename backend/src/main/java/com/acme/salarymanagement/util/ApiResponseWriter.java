package com.acme.salarymanagement.util;

import com.acme.salarymanagement.bean.ApiError;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.bean.ApiFieldError;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiResponseWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiResponseWriter.class);
    private static final String REQUEST_ID = ApiResponseWriter.class.getName() + ".requestId";
    private final ObjectMapper json;

    public ApiResponseWriter(ObjectMapper json) {
        this.json = json;
    }

    public String prepare(HttpServletRequest request, HttpServletResponse response) {
        String id = (String) request.getAttribute(REQUEST_ID);
        if (id == null) {
            id = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID, id);
        }
        response.setHeader("X-Request-ID", id);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return id;
    }

    public void error(HttpServletRequest request, HttpServletResponse response, int status,
            String code, String message, List<ApiFieldError> errors) throws IOException {
        String id = prepare(request, response);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), new ApiError(code, message, id, errors));
    }

    public <T> void json(HttpServletRequest request, HttpServletResponse response, int status, T body) throws IOException {
        prepare(request, response);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), body);
    }

    public void failure(HttpServletRequest request, HttpServletResponse response, ApiRequestException exception) throws IOException {
        if (exception.status().is5xxServerError() && exception.getCause() != null) {
            LOGGER.error("Authentication API dependency failed; requestId={}", prepare(request, response), exception.getCause());
        }
        error(request, response, exception.status().value(), exception.code(), exception.getMessage(), exception.fieldErrors());
    }

    public void unexpected(HttpServletRequest request, HttpServletResponse response, Exception exception) throws IOException {
        String id = prepare(request, response);
        LOGGER.error("Authentication API failed; requestId={}", id, exception);
        error(request, response, 500, "INTERNAL_ERROR", "Unable to complete the request.", List.of());
    }
}
