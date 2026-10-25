package com.acme.salarymanagement.validator;

import com.acme.salarymanagement.util.EmailUtility;
import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.bean.ReferenceDataResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EmployeeWriteValidator {
    private record EmailValue(@Email String value) { }
    private final Validator validator;
    private final Clock clock;
    public EmployeeWriteValidator(Validator validator, Clock clock) { this.validator=validator; this.clock=clock; }
    public List<ApiFieldError> validate(HttpServletRequest request, JsonNode body, String operation,
            String employeeId, ReferenceDataResponse references) {
        Set<String> fields = switch (operation) {
            case "create" -> Set.of("employeeId","fullName","workEmail","countryCode","department","jobTitle","jobLevel","annualBaseSalary","currency","joiningDate","initialSalaryEffectiveFrom");
            case "details" -> Set.of("fullName","workEmail","countryCode","department","jobTitle","jobLevel","joiningDate","lastEmploymentDate","version");
            default -> Set.of("employmentStatus","version");
        };
        List<ApiFieldError> errors = new ArrayList<>();
        if (!request.getParameterMap().isEmpty()) errors.add(error("query","UNKNOWN_FIELD","Query parameters are not accepted."));
        if (employeeId != null && !employeeId.matches("EMP-[0-9]{6}")) errors.add(error("employeeId","INVALID_FORMAT","Use EMP- followed by six digits."));
        body.fieldNames().forEachRemaining(field -> { if (!fields.contains(field)) errors.add(error(field,"UNKNOWN_FIELD","Unexpected field.")); });
        for (String field : fields) {
            JsonNode value = body.get(field);
            if (value == null || (value.isNull() && !field.equals("lastEmploymentDate"))) { errors.add(error(field,"REQUIRED","This field is required.")); continue; }
            if (value.isNull()) continue;
            if (field.equals("version")) {
                if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue()<0 || value.longValue()>9007199254740991L)
                    errors.add(error(field,"INVALID_TYPE","Supply a nonnegative safe integer version."));
                continue;
            }
            if (!value.isTextual()) { errors.add(error(field,"INVALID_TYPE","Supply a string.")); continue; }
            String text = field.equals("annualBaseSalary") ? value.textValue() : value.textValue().strip();
            ((ObjectNode)body).put(field,text);
            int limit = switch(field) { case "fullName","jobTitle" -> 200; case "workEmail" -> 254; default -> 1000; };
            if (text.isBlank()) errors.add(error(field,"REQUIRED","This field is required."));
            else if (text.codePointCount(0,text.length())>limit) errors.add(error(field,"TOO_LONG","This field exceeds its maximum length."));
            if (field.equals("employeeId") && !text.matches("EMP-[0-9]{6}")) errors.add(error(field,"INVALID_FORMAT","Use EMP- followed by six digits."));
            if (field.equals("workEmail")) {
                text = EmailUtility.normalize(text); ((ObjectNode)body).put(field,text);
                if (!validator.validate(new EmailValue(text)).isEmpty()) errors.add(error(field,"INVALID_FORMAT","Enter a valid work email."));
            }
            if (field.endsWith("Date") || field.equals("initialSalaryEffectiveFrom")) {
                try {
                    if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || LocalDate.parse(text).getYear()<1) throw new IllegalArgumentException();
                    if (!field.equals("lastEmploymentDate") && LocalDate.parse(text).isAfter(LocalDate.now(clock)))
                        errors.add(error(field,"FUTURE_DATE_NOT_ALLOWED","Date must not be in the future."));
                } catch (RuntimeException exception) { errors.add(error(field,"INVALID_DATE","Supply a valid YYYY-MM-DD date.")); }
            }
        }
        if (references != null) {
            Map<String,List<String>> supported=Map.of("countryCode",references.countries().stream().map(ReferenceDataResponse.Country::code).toList(),
                    "department",references.departments().stream().map(ReferenceDataResponse.Option::value).toList(),
                    "jobLevel",references.jobLevels().stream().map(ReferenceDataResponse.Option::value).toList(),
                    "currency",references.currencies().stream().map(ReferenceDataResponse.Currency::code).toList());
            supported.forEach((field,values)-> { if (body.has(field) && !values.contains(body.path(field).asText())) errors.add(error(field,"UNSUPPORTED_VALUE","Use a supported canonical value.")); });
        }
        if (operation.equals("status") && !body.path("employmentStatus").asText().equals("INACTIVE")) errors.add(error("employmentStatus","UNSUPPORTED_VALUE","Only INACTIVE is accepted."));
        if (operation.equals("create") && body.path("annualBaseSalary").isTextual()) {
            String amount=body.path("annualBaseSalary").textValue();
            int precision=references.currencies().stream().filter(c->c.code().equals(body.path("currency").asText())).mapToInt(ReferenceDataResponse.Currency::fractionDigits).findFirst().orElse(2);
            if (!amount.matches("[0-9]{1,15}(\\.[0-9]{1,"+precision+"})?")) errors.add(error("annualBaseSalary","INVALID_PRECISION","Use a plain decimal within the currency precision and amount range."));
            else if (new BigDecimal(amount).signum()<=0) errors.add(error("annualBaseSalary","POSITIVE_AMOUNT_REQUIRED","Salary must be positive."));
        }
        if (errors.isEmpty() && (operation.equals("create") || operation.equals("details"))) {
            LocalDate joining=LocalDate.parse(body.path("joiningDate").asText());
            String endField=operation.equals("create")?"initialSalaryEffectiveFrom":"lastEmploymentDate";
            if (!body.path(endField).isNull() && LocalDate.parse(body.path(endField).asText()).isBefore(joining)) errors.add(error(endField,"INVALID_DATE_ORDER","Date must be on or after joining date."));
        }
        return errors;
    }
    private ApiFieldError error(String field,String code,String message) { return new ApiFieldError(field,code,message); }
}
