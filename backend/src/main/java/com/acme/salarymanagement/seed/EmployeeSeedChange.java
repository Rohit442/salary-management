package com.acme.salarymanagement.seed;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Random;
import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.InsertStatement;
import net.datafaker.Faker;

/** Liquibase owns execution, connection lifecycle, transaction and applied-change tracking. */
public class EmployeeSeedChange implements CustomSqlChange {
    private static final String[] COUNTRIES = {"IN", "US", "GB", "DE", "SG"};
    private static final String[] CURRENCIES = {"INR", "USD", "GBP", "EUR", "SGD"};
    private static final String[] DEPARTMENTS = {"Engineering", "Product", "Design", "Finance", "Operations", "HR"};
    private static final String[] TITLES = {"Software Engineer", "Product Manager", "Designer", "Financial Analyst", "Operations Analyst", "HR Specialist"};
    private static final long[] SALARY_FLOORS = {400000, 40000, 30000, 35000, 45000};
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 1, 1);
    private int employeeCount = 10000;

    public void setEmployeeCount(String employeeCount) {
        this.employeeCount = Integer.parseInt(employeeCount);
    }

    @Override
    public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
        if (employeeCount < 1 || employeeCount > 10000) {
            throw new CustomChangeException("employeeCount must be between 1 and 10000");
        }
        Random random = new Random(42);
        Faker faker = new Faker(Locale.ENGLISH, new Random(42));
        SqlStatement[] statements = new SqlStatement[employeeCount];
        for (int index = 0; index < employeeCount; index++) {
            int country = index % COUNTRIES.length;
            int department = index % DEPARTMENTS.length;
            int level = 1 + (index / DEPARTMENTS.length + index) % 6;
            String employeeId = String.format(Locale.ROOT, "EMP-%06d", index + 1);
            LocalDate joiningDate = REFERENCE_DATE.minusDays(365 + random.nextInt(3285));
            boolean inactive = index % 11 == 10;
            BigDecimal salary = BigDecimal.valueOf(SALARY_FLOORS[country] * level + random.nextInt(20000));
            statements[index] = new InsertStatement(database.getDefaultCatalogName(), database.getDefaultSchemaName(), "employee")
                    .addColumnValue("employee_id", employeeId)
                    .addColumnValue("full_name", faker.name().fullName())
                    .addColumnValue("work_email", employeeId.toLowerCase(Locale.ROOT) + "@acme.example")
                    .addColumnValue("country_code", COUNTRIES[country])
                    .addColumnValue("department", DEPARTMENTS[department])
                    .addColumnValue("job_title", TITLES[department])
                    .addColumnValue("job_level", "L" + level)
                    .addColumnValue("employment_status", inactive ? "INACTIVE" : "ACTIVE")
                    .addColumnValue("annual_base_salary", salary)
                    .addColumnValue("currency_code", CURRENCIES[country])
                    .addColumnValue("joining_date", Date.valueOf(joiningDate))
                    .addColumnValue("last_employment_date", inactive ? Date.valueOf(REFERENCE_DATE.minusDays(random.nextInt(180))) : null)
                    .addColumnValue("initial_annual_base_salary", salary)
                    .addColumnValue("initial_salary_effective_from", Date.valueOf(joiningDate))
                    .addColumnValue("salary_effective_from", Date.valueOf(joiningDate))
                    .addColumnValue("version", 0L)
                    .addColumnValue("created_at", "2026-01-01T00:00:00Z")
                    .addColumnValue("updated_at", "2026-01-01T00:00:00Z");
        }
        return statements;
    }

    @Override
    public ValidationErrors validate(Database database) {
        ValidationErrors errors = new ValidationErrors();
        if (employeeCount < 1 || employeeCount > 10000) {
            errors.addError("employeeCount must be between 1 and 10000");
        }
        return errors;
    }

    @Override public String getConfirmationMessage() { return "Seeded " + employeeCount + " fictional employees"; }
    @Override public void setUp() { }
    @Override public void setFileOpener(ResourceAccessor resourceAccessor) { }
}
