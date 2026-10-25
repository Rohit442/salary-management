package com.acme.salarymanagement.bean;

import java.util.List;
import java.util.Map;

public record EmployeeQuery(Map<String, List<String>> filters, String search, int page, int size,
        String sortField, String sortDirection) { }
