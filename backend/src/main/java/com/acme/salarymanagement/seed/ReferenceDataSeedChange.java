package com.acme.salarymanagement.seed;

import java.util.ArrayList;
import java.util.UUID;
import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.InsertStatement;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Reference values and a disabled provenance identity, not a provisioned HR login. */
public class ReferenceDataSeedChange implements CustomSqlChange {
    @Override
    public SqlStatement[] generateStatements(Database database) {
        var statements = new ArrayList<SqlStatement>();
        statements.add(new InsertStatement(database.getDefaultCatalogName(), database.getDefaultSchemaName(), "hr_user")
                .addColumnValue("work_email", "seed@acme.example")
                .addColumnValue("password_hash", "{bcrypt}" + new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()))
                .addColumnValue("display_name", "Demo Data Seed")
                .addColumnValue("role", "HR")
                .addColumnValue("enabled", false));
        String[][] currencies = {
                {"INR", "Indian Rupee"}, {"USD", "US Dollar"}, {"GBP", "Pound Sterling"},
                {"EUR", "Euro"}, {"SGD", "Singapore Dollar"}
        };
        for (String[] currency : currencies) {
            statements.add(new InsertStatement(database.getDefaultCatalogName(), database.getDefaultSchemaName(), "currency")
                    .addColumnValue("code", currency[0])
                    .addColumnValue("fraction_digits", 2)
                    .addColumnValue("name", currency[1]));
        }
        return statements.toArray(SqlStatement[]::new);
    }

    @Override public ValidationErrors validate(Database database) { return new ValidationErrors(); }
    @Override public String getConfirmationMessage() { return "Seeded currencies and disabled employee-seed audit actor"; }
    @Override public void setUp() { }
    @Override public void setFileOpener(ResourceAccessor resourceAccessor) { }
}
