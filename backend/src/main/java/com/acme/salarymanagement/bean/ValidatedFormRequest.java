package com.acme.salarymanagement.bean;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

public class ValidatedFormRequest extends HttpServletRequestWrapper {
    private final Map<String, String[]> fields;

    public ValidatedFormRequest(HttpServletRequest request, Map<String, String[]> fields) {
        super(request);
        this.fields = Collections.unmodifiableMap(fields);
    }
    @Override public String getParameter(String name) {
        var values = fields.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }
    @Override public String[] getParameterValues(String name) {
        var values = fields.get(name);
        return values == null ? null : values.clone();
    }
    @Override public Map<String, String[]> getParameterMap() { return fields; }
    @Override public Enumeration<String> getParameterNames() { return Collections.enumeration(fields.keySet()); }
}
