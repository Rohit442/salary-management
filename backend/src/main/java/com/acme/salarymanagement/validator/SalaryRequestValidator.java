package com.acme.salarymanagement.validator;

import com.acme.salarymanagement.bean.ApiFieldError;
import com.acme.salarymanagement.exception.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SalaryRequestValidator {
    public List<ApiFieldError> validateHistory(HttpServletRequest request,String id) throws IOException {
        if (request.getInputStream().read()!=-1) throw new ApiRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"UNSUPPORTED_MEDIA_TYPE","This endpoint does not accept a body.",List.of());
        List<ApiFieldError> errors=new ArrayList<>();
        if (!id.matches("EMP-[0-9]{6}")) errors.add(new ApiFieldError("employeeId","INVALID_FORMAT","Use EMP- followed by six digits."));
        for(var entry:request.getParameterMap().entrySet()) {
            String field=entry.getKey();
            if (!Set.of("page","size").contains(field)) { errors.add(new ApiFieldError(field,"INVALID_PARAMETER","Unexpected query parameter."));continue; }
            try {
                if(entry.getValue().length!=1 || !entry.getValue()[0].matches("[0-9]+")) throw new NumberFormatException();
                int value=Integer.parseInt(entry.getValue()[0]);
                if(value<(field.equals("size")?1:0) || value>(field.equals("size")?100:1000000)) throw new NumberFormatException();
            } catch(NumberFormatException exception) { errors.add(new ApiFieldError(field,"INVALID_PARAMETER","Supply an integer in the supported page/size range.")); }
        }
        return errors;
    }
    public List<ApiFieldError> validateEstimate(HttpServletRequest request,String id) throws IOException {
        if(request.getInputStream().read()!=-1) throw new ApiRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"UNSUPPORTED_MEDIA_TYPE","This endpoint does not accept a body.",List.of());
        List<ApiFieldError> errors=new ArrayList<>();
        if(!id.matches("EMP-[0-9]{6}")) errors.add(new ApiFieldError("employeeId","INVALID_FORMAT","Use EMP- followed by six digits."));
        for(String field:request.getParameterMap().keySet()) if(!field.equals("month")) errors.add(new ApiFieldError(field,"INVALID_PARAMETER","Unexpected query parameter."));
        String[] months=request.getParameterValues("month");
        try {
            if(months==null || months.length!=1 || !months[0].matches("[0-9]{4}-[0-9]{2}") || java.time.YearMonth.parse(months[0]).getYear()<1) throw new IllegalArgumentException();
        } catch(RuntimeException exception) { errors.add(new ApiFieldError("month","INVALID_FORMAT","Supply exactly one valid YYYY-MM month.")); }
        return errors;
    }
    public List<ApiFieldError> validateKey(HttpServletRequest request) {
        var keys=Collections.list(request.getHeaders("Idempotency-Key"));
        return keys.size()==1 && keys.getFirst().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") ? List.of()
                : List.of(new ApiFieldError("Idempotency-Key","INVALID_FORMAT","Supply one canonical lowercase UUID for this operation."));
    }
    public int page(HttpServletRequest request) { return Integer.parseInt(request.getParameter("page")==null?"0":request.getParameter("page")); }
    public int size(HttpServletRequest request) { return Integer.parseInt(request.getParameter("size")==null?"25":request.getParameter("size")); }
}
