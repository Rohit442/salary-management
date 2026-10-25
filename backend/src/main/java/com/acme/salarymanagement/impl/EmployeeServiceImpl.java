package com.acme.salarymanagement.impl;

import com.acme.salarymanagement.bean.ApiFieldError;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import com.acme.salarymanagement.bean.EmployeePage;
import com.acme.salarymanagement.bean.EmployeeQuery;
import com.acme.salarymanagement.bean.EmployeeResponse;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.service.EmployeeService;
import com.acme.salarymanagement.util.SqlUtility;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeServiceImpl implements EmployeeService {
    private static final Map<String, String> SORT_COLUMNS = Map.of("fullName", "e.full_name", "employeeId", "e.employee_id",
            "countryCode", "e.country_code", "department", "e.department", "jobLevel", "e.job_level", "employmentStatus", "e.employment_status");
    private static final Map<String, String> FILTER_COLUMNS = Map.of("country", "e.country_code", "department", "e.department",
            "jobLevel", "e.job_level", "currency", "e.currency_code");
    private static final String SELECT = "SELECT e.*, c.fraction_digits FROM employee e JOIN currency c ON c.code=e.currency_code";
    private final SqlUtility sql;
    private final Clock clock;
    public EmployeeServiceImpl(SqlUtility sql,Clock clock) { this.sql = sql; this.clock=clock; }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public EmployeePage list(EmployeeQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        for (var filter : query.filters().entrySet()) if (!filter.getValue().isEmpty()) {
            where.append(" AND ").append(FILTER_COLUMNS.get(filter.getKey())).append(" IN (")
                    .append(String.join(",", Collections.nCopies(filter.getValue().size(), "?"))).append(")");
            parameters.addAll(filter.getValue());
        }
        if (!query.search().isEmpty()) {
            String literal = query.search().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
            where.append(" AND (LOWER(e.full_name) LIKE ? ESCAPE '!' OR LOWER(e.employee_id) LIKE ? ESCAPE '!')");
            parameters.add("%" + literal + "%"); parameters.add("%" + literal + "%");
        }
        try {
            long total = sql.queryOne("SELECT COUNT(*) FROM employee e" + where, (row, index) -> row.getLong(1), parameters.toArray());
            String order = " ORDER BY LOWER(" + SORT_COLUMNS.get(query.sortField()) + ") "
                    + (query.sortDirection().equals("desc") ? "DESC" : "ASC") + ", e.id ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            parameters.add((long) query.page() * query.size()); parameters.add(query.size());
            var items = sql.query(SELECT + where + order, this::employee, parameters.toArray());
            long totalPages = (total + query.size() - 1) / query.size();
            return new EmployeePage(items, query.page(), query.size(), items.size(), total, totalPages,
                    new EmployeePage.Sort(query.sortField(), query.sortDirection()), query.page() > 0, query.page() + 1L < totalPages);
        } catch (DataAccessException exception) { throw unavailable(exception); }
    }

    @Override
    public EmployeeResponse details(String employeeId) {
        try {
            var items = sql.query(SELECT + " WHERE e.employee_id=?", this::employee, employeeId);
            if (items.isEmpty()) throw new ApiRequestException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "Employee was not found.", List.of());
            return items.getFirst();
        } catch (DataAccessException exception) { throw unavailable(exception); }
    }


    private EmployeeResponse employee(ResultSet row, int index) throws SQLException {
        return new EmployeeResponse(row.getString("employee_id"), row.getString("full_name"), row.getString("work_email"),
                row.getString("country_code"), row.getString("department"), row.getString("job_title"), row.getString("job_level"),
                row.getString("employment_status"), row.getBigDecimal("annual_base_salary")
                        .setScale(row.getInt("fraction_digits"), RoundingMode.HALF_UP).toPlainString(),
                row.getString("currency_code"), row.getLong("version"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(), row.getObject("updated_at", OffsetDateTime.class).toInstant(),
                row.getObject("joining_date", LocalDate.class), row.getObject("last_employment_date", LocalDate.class),
                row.getObject("initial_salary_effective_from", LocalDate.class), row.getObject("salary_effective_from", LocalDate.class));
    }
    private ApiRequestException unavailable(DataAccessException cause) {
        return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Employee data is temporarily unavailable. Please try again.", List.of(), cause);
    }
}
