package com.acme.salarymanagement.validator;

import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.bean.EmployeeQuery;
import com.acme.salarymanagement.bean.ReferenceDataResponse;
import com.acme.salarymanagement.exception.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EmployeeRequestValidator {
    public static final Set<String> SORT_FIELDS = Set.of("fullName", "employeeId", "countryCode", "department", "jobLevel", "employmentStatus");
    private static final Set<String> SCALARS = Set.of("q", "page", "size", "sort");

    public List<ApiFieldError> validateList(HttpServletRequest request, ReferenceDataResponse references) throws IOException {
        rejectBody(request);
        List<ApiFieldError> errors = new ArrayList<>();
        Map<String, List<String>> filters = Map.of(
                "country", references.countries().stream().map(ReferenceDataResponse.Country::code).toList(),
                "department", references.departments().stream().map(ReferenceDataResponse.Option::value).toList(),
                "jobLevel", references.jobLevels().stream().map(ReferenceDataResponse.Option::value).toList(),
                "currency", references.currencies().stream().map(ReferenceDataResponse.Currency::code).toList());
        for (var entry : request.getParameterMap().entrySet()) {
            String field = entry.getKey();
            String[] values = entry.getValue();
            if (filters.containsKey(field)) {
                if (values.length > 20) errors.add(error(field, "TOO_MANY_VALUES", "Supply at most 20 filter values."));
                if (Arrays.stream(values).anyMatch(value -> !filters.get(field).contains(value))) {
                    errors.add(error(field, "UNSUPPORTED_VALUE", "Supply a supported canonical value."));
                }
            } else if (!SCALARS.contains(field)) errors.add(error(field, "UNKNOWN_PARAMETER", "Unexpected query parameter."));
            else if (values.length != 1) errors.add(error(field, "DUPLICATE", "Supply this parameter exactly once."));
        }
        validateInteger(request, "page", 0, 1000000, errors);
        validateInteger(request, "size", 1, 100, errors);
        String search = request.getParameter("q");
        if (search != null && (search.trim().isEmpty() || search.trim().codePointCount(0, search.trim().length()) > 100)) {
            errors.add(error("q", "INVALID_LENGTH", "Search must contain 1–100 characters."));
        }
        String sort = request.getParameter("sort");
        if (sort != null) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !SORT_FIELDS.contains(parts[0]) || !Set.of("asc", "desc").contains(parts[1])) {
                errors.add(error("sort", "UNSUPPORTED_VALUE", "Supply a supported sort field and asc or desc."));
            }
        }
        return errors;
    }

    public List<ApiFieldError> validateDetails(HttpServletRequest request, String id) throws IOException {
        rejectBody(request);
        List<ApiFieldError> errors = new ArrayList<>();
        if (!id.matches("EMP-[0-9]{6}")) errors.add(error("employeeId", "INVALID_FORMAT", "Use EMP- followed by six digits."));
        if (!request.getParameterMap().isEmpty()) errors.add(error("query", "NOT_ALLOWED", "Query parameters are not accepted."));
        return errors;
    }

    public EmployeeQuery query(HttpServletRequest request) {
        Map<String, List<String>> filters = new LinkedHashMap<>();
        for (String field : DashboardRequestValidator.FILTERS) {
            String[] values = request.getParameterValues(field);
            filters.put(field, values == null ? List.of() : Arrays.stream(values).distinct().sorted().toList());
        }
        String[] sort = defaultValue(request, "sort", "fullName,asc").split(",");
        return new EmployeeQuery(filters, defaultValue(request, "q", "").trim(),
                Integer.parseInt(defaultValue(request, "page", "0")), Integer.parseInt(defaultValue(request, "size", "25")), sort[0], sort[1]);
    }
    private void validateInteger(HttpServletRequest request, String field, int minimum, int maximum, List<ApiFieldError> errors) {
        String value = request.getParameter(field);
        if (value == null) return;
        try {
            if (!value.matches("[0-9]+") || Integer.parseInt(value) < minimum || Integer.parseInt(value) > maximum) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            errors.add(error(field, "OUT_OF_RANGE", "Supply an integer from " + minimum + " to " + maximum + "."));
        }
    }
    private String defaultValue(HttpServletRequest request, String key, String fallback) {
        String value = request.getParameter(key);
        return value == null ? fallback : value;
    }
    private void rejectBody(HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) throw new ApiRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE", "This endpoint does not accept a request body.", List.of());
    }
    private ApiFieldError error(String field, String code, String message) { return new ApiFieldError(field, code, message); }
}
