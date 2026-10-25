package com.acme.salarymanagement.impl;

import com.acme.salarymanagement.bean.CompensationReport;
import com.acme.salarymanagement.bean.CompensationReport.CurrencySummary;
import com.acme.salarymanagement.bean.CompensationReport.Group;
import com.acme.salarymanagement.bean.ReferenceDataResponse;
import com.acme.salarymanagement.bean.ReferenceDataResponse.Currency;
import com.acme.salarymanagement.config.ReferenceVocabulary;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.service.DashboardService;
import com.acme.salarymanagement.util.SqlUtility;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {
    private record Salary(Map<String, String> dimensions, BigDecimal amount, int precision) { }
    private record Metrics(int count, String total, String average, String median) { }
    private final SqlUtility sql;
    private final Clock clock;
    public DashboardServiceImpl(SqlUtility sql, Clock clock) { this.sql = sql; this.clock = clock; }

    @Override
    public ReferenceDataResponse referenceData() {
        try {
            var currencies = sql.query("SELECT code, name, fraction_digits FROM currency ORDER BY code",
                    (row, index) -> new Currency(row.getString("code"), row.getString("name"), row.getInt("fraction_digits")));
            if (currencies.isEmpty() || currencies.stream().anyMatch(value -> value.code() == null
                    || value.code().isBlank() || value.label() == null || value.label().isBlank()
                    || value.fractionDigits() < 0 || value.fractionDigits() > 4)) throw unavailable(null);
            return new ReferenceDataResponse(ReferenceVocabulary.COUNTRIES, ReferenceVocabulary.DEPARTMENTS,
                    ReferenceVocabulary.LEVELS, currencies);
        } catch (DataAccessException exception) { throw unavailable(exception); }
    }

    @Override
    public CompensationReport compensation(Map<String, List<String>> filters, List<String> groupBy) {
        // One statement captures the entire matching population and currency precision in one H2 snapshot.
        StringBuilder query = new StringBuilder("""
                SELECT e.country_code, e.department, e.job_level, e.currency_code, e.annual_base_salary,
                       c.fraction_digits FROM employee e JOIN currency c ON c.code=e.currency_code
                WHERE e.employment_status='ACTIVE'
                """);
        Map<String, String> columns = Map.of("country", "e.country_code", "department", "e.department",
                "jobLevel", "e.job_level", "currency", "e.currency_code");
        List<Object> parameters = new ArrayList<>();
        for (var entry : filters.entrySet()) if (!entry.getValue().isEmpty()) {
            query.append(" AND ").append(columns.get(entry.getKey())).append(" IN (")
                    .append(String.join(",", java.util.Collections.nCopies(entry.getValue().size(), "?"))).append(")");
            parameters.addAll(entry.getValue());
        }
        List<Salary> rows;
        try {
            rows = sql.query(query.toString(), (row, index) -> new Salary(Map.of(
                    "country", row.getString("country_code"), "department", row.getString("department"),
                    "jobLevel", row.getString("job_level"), "currency", row.getString("currency_code")),
                    row.getBigDecimal("annual_base_salary"), row.getInt("fraction_digits")), parameters.toArray());
        } catch (DataAccessException exception) { throw unavailable(exception); }
        Map<String, List<Salary>> currencies = new TreeMap<>();
        Map<List<String>, List<Salary>> grouped = new TreeMap<>((left, right) -> {
            for (int i = 0; i < left.size(); i++) {
                int difference = left.get(i).compareTo(right.get(i));
                if (difference != 0) return difference;
            }
            return 0;
        });
        List<String> sortKeys = new ArrayList<>(List.of("currency"));
        sortKeys.addAll(groupBy.stream().filter(key -> !key.equals("currency")).toList());
        for (Salary row : rows) {
            currencies.computeIfAbsent(row.dimensions().get("currency"), key -> new ArrayList<>()).add(row);
            var key = sortKeys.stream().map(row.dimensions()::get).toList();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<CurrencySummary> summaries = new ArrayList<>();
        currencies.forEach((currency, salaries) -> {
            var metric = metrics(salaries);
            summaries.add(new CurrencySummary(currency, metric.count(), metric.total(), metric.average(), metric.median()));
        });
        List<Group> groups = new ArrayList<>();
        for (var salaries : grouped.values()) {
            Map<String, String> dimensions = new LinkedHashMap<>();
            for (String key : groupBy) dimensions.put(key, salaries.getFirst().dimensions().get(key));
            var metric = metrics(salaries);
            groups.add(new Group(dimensions, metric.count(), metric.total(), metric.average(), metric.median()));
        }
        return new CompensationReport(filters, groupBy, rows.size(), summaries, groups, clock.instant());
    }

    private Metrics metrics(List<Salary> rows) {
        var amounts = rows.stream().map(Salary::amount).sorted(Comparator.naturalOrder()).toList();
        int count = amounts.size();
        int precision = rows.getFirst().precision();
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal median = count % 2 == 1 ? amounts.get(count / 2)
                : amounts.get(count / 2 - 1).add(amounts.get(count / 2)).divide(BigDecimal.valueOf(2));
        return new Metrics(count, sum.setScale(precision, RoundingMode.HALF_UP).toPlainString(),
                sum.divide(BigDecimal.valueOf(count), precision, RoundingMode.HALF_UP).toPlainString(),
                median.setScale(precision, RoundingMode.HALF_UP).toPlainString());
    }
    private ApiRequestException unavailable(Exception cause) {
        return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Dashboard data is temporarily unavailable. Please try again.", List.of(), cause);
    }
}
