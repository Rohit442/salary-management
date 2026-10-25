package com.acme.salarymanagement.bean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CompensationReport(Map<String, List<String>> appliedFilters, List<String> groupBy,
        int totalEmployees, List<CurrencySummary> currencySummaries, List<Group> groups, Instant generatedAt) {
    public record CurrencySummary(String currency, int employeeCount, String totalAnnualBaseSalary,
            String averageAnnualBaseSalary, String medianAnnualBaseSalary) { }
    public record Group(Map<String, String> dimensions, int employeeCount, String totalAnnualBaseSalary,
            String averageAnnualBaseSalary, String medianAnnualBaseSalary) { }
}
