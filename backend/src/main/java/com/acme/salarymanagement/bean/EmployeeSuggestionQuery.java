package com.acme.salarymanagement.bean;

import java.util.List;
import java.util.Map;

public record EmployeeSuggestionQuery(Map<String, List<String>> filters, String prefix, int limit) { }
