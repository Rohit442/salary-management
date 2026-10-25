package com.acme.salarymanagement.validator;

import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.bean.EmployeeSuggestionQuery;
import com.acme.salarymanagement.exception.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EmployeeSuggestionValidator {
    private static final Set<String> PARAMETERS = Set.of("prefix", "limit");

    public List<ApiFieldError> validate(HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) {
            throw new ApiRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                    "This endpoint does not accept a request body.", List.of());
        }
        List<ApiFieldError> errors = new ArrayList<>();
        for (var entry : request.getParameterMap().entrySet()) {
            if (!PARAMETERS.contains(entry.getKey())) {
                errors.add(new ApiFieldError(entry.getKey(), "UNKNOWN_PARAMETER", "Unexpected query parameter."));
            } else if (entry.getValue().length != 1) {
                errors.add(new ApiFieldError(entry.getKey(), "DUPLICATE", "Supply this parameter exactly once."));
            }
        }
        String prefix = request.getParameter("prefix");
        if (prefix == null) {
            errors.add(new ApiFieldError("prefix", "REQUIRED", "Supply the beginning of an employee name."));
        } else {
            String trimmed = prefix.trim();
            if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > 100) {
                errors.add(new ApiFieldError("prefix", "INVALID_LENGTH", "Prefix must contain 1–100 characters."));
            }
        }
        String limit = request.getParameter("limit");
        if (limit != null) {
            try {
                if (!limit.matches("[0-9]+") || Integer.parseInt(limit) < 1 || Integer.parseInt(limit) > 10) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                errors.add(new ApiFieldError("limit", "OUT_OF_RANGE", "Supply an integer from 1 to 10."));
            }
        }
        return errors;
    }

    public EmployeeSuggestionQuery query(HttpServletRequest request) {
        String limit = request.getParameter("limit");
        return new EmployeeSuggestionQuery(request.getParameter("prefix").trim(), limit == null ? 8 : Integer.parseInt(limit));
    }
}
