# H2 database and Liquibase setup

The backend uses embedded, file-based H2. Maven supplies H2 and Liquibase; no database server or container is needed.

## Run and inspect the sample

Use JDK 21 and run `./mvnw spring-boot:run` from `backend/`, or run `SalaryManagementApplication` in IntelliJ. Remove old Oracle `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` overrides from the run configuration.

The default JDBC URL is `jdbc:h2:file:~/.acme-salary-management/salary-management;DB_CLOSE_ON_EXIT=FALSE`. H2 stores the database under your home directory, so starting from the project root or backend folder opens the same database. Restarting the backend retains data. Stop the current backend before launching another instance against this embedded file.

Optional overrides are `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; defaults are the URL above, `sa`, and an empty password. Keep local database files outside source control.

Liquibase reads `backend/src/main/resources/db/changelog/db.changelog-master.yaml`. Its two changesets create `sample_message` and insert one row with ID `1` and message `hello Rohit`. Applied changesets are recorded in `DATABASECHANGELOG`; restarting neither duplicates the seed nor overwrites edited rows. Add new changesets for later changes rather than editing applied ones.

Spring Security currently supplies development authentication: username `user` and the generated password printed at startup. Use that password when prompted by curl:

```sh
curl --user user --header 'Accept: application/json' http://localhost:8080/api/sample
```

Expected HTTP 200 body:

```json
{"id":1,"message":"hello Rohit"}
```

This endpoint reads the row through the SQL utility, using Spring JDBC to acquire and release pooled connections. The named HR session endpoints remain part of the application implementation plan.

## Tests and larger datasets

`./mvnw verify` runs tests against a separate in-memory H2 database and temporary file databases. The restart test closes and reopens an application context, verifies saved data survives, and checks the seed has not run twice.

The future 10,000-employee dataset will use explicitly selected Liquibase seed contexts, separate from small test fixtures. The current implemented seed contains only the one sample row.

Use `BigDecimal` and `DECIMAL(19,4)` for salary amounts when implementing the employee schema. Validate constraints, reports, transactions, and retry behavior against H2 using the same Liquibase migrations.

References: [H2 features](https://h2database.github.io/html/features.html), [Spring database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html), [Liquibase configuration](https://contribute.liquibase.com/extensions-integrations/directory/integration-docs/springboot/configuration/).
