package com.acme.salarymanagement.validator;

import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.bean.ReferenceDataResponse;
import com.acme.salarymanagement.exception.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DashboardRequestValidator {
    public static final List<String> DIMENSIONS = List.of("country", "department", "jobLevel");
    public static final List<String> FILTERS = List.of("country", "department", "jobLevel", "currency");
    public List<ApiFieldError> validate(HttpServletRequest request, ReferenceDataResponse references,
            boolean report) throws IOException {
        if (request.getInputStream().read() != -1) {
            throw new ApiRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                    "This endpoint does not accept a request body.", List.of());
        }
        Map<String, List<String>> supported = !report ? Map.of() : Map.of(
                "country", references.countries().stream().map(ReferenceDataResponse.Country::code).toList(),
                "department", references.departments().stream().map(ReferenceDataResponse.Option::value).toList(),
                "jobLevel", references.jobLevels().stream().map(ReferenceDataResponse.Option::value).toList(),
                "currency", references.currencies().stream().map(ReferenceDataResponse.Currency::code).toList(),
                "groupBy", DIMENSIONS);
        List<ApiFieldError> errors = new ArrayList<>();
        for (var entry : request.getParameterMap().entrySet()) {
            String field = entry.getKey();
            if (!report || !supported.containsKey(field)) {
                errors.add(new ApiFieldError(field, "UNKNOWN_PARAMETER", "Unexpected query parameter."));
                continue;
            }
            if (!field.equals("groupBy") && entry.getValue().length > 20) {
                errors.add(new ApiFieldError(field, "TOO_MANY_VALUES", "Supply at most 20 values per filter."));
            }
            for (String value : entry.getValue()) {
                if (!supported.get(field).contains(value)) {
                    errors.add(new ApiFieldError(field, "UNSUPPORTED_VALUE", "Supply a supported canonical value."));
                    break;
                }
            }
        }
        return errors;
    }
    public Map<String, List<String>> filters(HttpServletRequest request) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        for (String key : FILTERS) {
            String[] values = request.getParameterValues(key);
            result.put(key, values == null ? List.of() : java.util.Arrays.stream(values).distinct().sorted().toList());
        }
        return result;
    }
    public List<String> grouping(HttpServletRequest request) {
        var supplied = request.getParameterValues("groupBy");
        var values = supplied == null ? List.<String>of() : java.util.Arrays.asList(supplied);
        List<String> result = new ArrayList<>();
        for (String key : DIMENSIONS) if (values.contains(key)) result.add(key);
        result.add("currency");
        return List.copyOf(result);
    }
}
