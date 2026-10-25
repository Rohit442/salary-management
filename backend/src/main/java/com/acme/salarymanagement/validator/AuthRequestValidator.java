package com.acme.salarymanagement.validator;

import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.exception.ApiRequestException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import com.acme.salarymanagement.util.EmailUtility;
import com.acme.salarymanagement.util.FormUtility;
import java.util.Comparator;
import java.util.Map;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthRequestValidator {
    private record EmailField(@Email String value) { }
    private static final int MAX_BODY_BYTES = 4096;
    private static final Map<String, String> METHODS = Map.of(
            "/api/auth/csrf", "GET", "/api/auth/me", "GET",
            "/api/auth/login", "POST", "/api/auth/logout", "POST",
            "/api/reports/compensation", "GET", "/api/reference-data", "GET");
    private final Validator beanValidator;

    public AuthRequestValidator(Validator beanValidator) { this.beanValidator = beanValidator; }

    public String allowedMethod(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/employees")) return "GET, POST";
        if (path.matches("/api/employees/[^/]+/(details|status)")) return "PUT";
        return path.matches("/api/employees/[^/]+") ? "GET" : METHODS.get(path);
    }

    public void validateProtocol(HttpServletRequest request, String path) {
        String method = allowedMethod(request);
        if (method != null && !java.util.Arrays.asList(method.split(", ")).contains(request.getMethod())) {
            throw invalid(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This HTTP method is not supported.");
        }
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) return;
        try {
            var matching = MediaType.parseMediaTypes(accept).stream()
                    .filter(type -> type.isCompatibleWith(MediaType.APPLICATION_JSON))
                    .sorted(Comparator.<MediaType>comparingInt(type -> type.isWildcardType() ? 0 : type.isWildcardSubtype() ? 1 : 2)
                            .reversed().thenComparing(Comparator.comparingDouble(MediaType::getQualityValue).reversed()))
                    .toList();
            if (matching.isEmpty() || matching.getFirst().getQualityValue() == 0) {
                throw invalid(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "This endpoint returns JSON.");
            }
        } catch (org.springframework.http.InvalidMediaTypeException exception) {
            throw invalid(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed Accept header.");
        }
    }

    public Map<String, String[]> readLoginForm(HttpServletRequest request) throws IOException {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            throw invalid(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Query parameters are not accepted.");
        }
        try {
            MediaType type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
            if (type == null || !type.getType().equals("application") || !type.getSubtype().equals("x-www-form-urlencoded")
                    || (type.getCharset() != null && !type.getCharset().equals(java.nio.charset.StandardCharsets.UTF_8))) {
                throw invalid(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Sign-in requires a UTF-8 form body.");
            }
        } catch (org.springframework.http.InvalidMediaTypeException exception) {
            throw invalid(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Sign-in requires a UTF-8 form body.");
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw invalid(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Sign-in body is too large.");
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) throw invalid(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Sign-in body is too large.");
        try {
            // Servlet/test clients may already supply parsed fields when the stream is empty.
            return body.length == 0 ? request.getParameterMap() : FormUtility.decode(body);
        } catch (IllegalArgumentException exception) {
            throw invalid(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed form encoding.");
        }
    }

    public List<ApiFieldError> validateLoginFields(Map<String, String[]> fields) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (String name : fields.keySet()) {
            if (!name.equals("email") && !name.equals("password")) {
                errors.add(new ApiFieldError("form", "UNKNOWN_FIELD", "Unexpected form field."));
            }
        }
        for (String name : List.of("email", "password")) {
            String[] values = fields.get(name);
            if (values == null || values.length == 0 || values[0] == null || values[0].isEmpty()) {
                errors.add(new ApiFieldError(name, "REQUIRED", "This field is required."));
                continue;
            }
            if (values.length != 1) {
                errors.add(new ApiFieldError(name, "DUPLICATE", "Supply this field exactly once."));
                continue;
            }
            String value = name.equals("email") ? EmailUtility.normalize(values[0]) : values[0];
            int maximum = name.equals("email") ? 254 : 128;
            if (value.isEmpty()) errors.add(new ApiFieldError(name, "REQUIRED", "This field is required."));
            else if (value.codePointCount(0, value.length()) > maximum) {
                errors.add(new ApiFieldError(name, "TOO_LONG", "This field exceeds its maximum length."));
            } else if (name.equals("email") && !beanValidator.validate(new EmailField(value)).isEmpty()) {
                errors.add(new ApiFieldError(name, "INVALID_FORMAT", "Enter a valid work email."));
            }
        }
        return errors;
    }

    private ApiRequestException invalid(HttpStatus status, String code, String message) {
        return new ApiRequestException(status, code, message, List.of());
    }

    public List<ApiFieldError> validateBodylessRequest(HttpServletRequest request) throws IOException {
        // Read at most one byte; do not trust Content-Length to detect a body.
        if (request.getInputStream().read() != -1) {
            throw new ApiRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                    "This endpoint does not accept a request body.", List.of());
        }
        List<ApiFieldError> errors = new ArrayList<>();
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            errors.add(new ApiFieldError("query", "NOT_ALLOWED", "Query parameters are not accepted."));
        }
        return errors;
    }
}
