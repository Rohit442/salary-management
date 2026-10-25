# H2 database and Liquibase setup

The backend uses embedded, file-based H2. Maven supplies H2 and Liquibase; no database server or container is needed.

## Run and inspect the sample

Use JDK 21 and run `./mvnw spring-boot:run` from `backend/`, or run `SalaryManagementApplication` in IntelliJ. Remove old Oracle `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` overrides from the run configuration.

The default JDBC URL is `jdbc:h2:file:~/.acme-salary-management/salary-management;DB_CLOSE_ON_EXIT=FALSE`. H2 stores the database under your home directory, so starting from the project root or backend folder opens the same database. Restarting the backend retains data. Stop the current backend before launching another instance against this embedded file.

Optional overrides are `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; defaults are the URL above, `sa`, and an empty password. Keep local database files outside source control.

Liquibase reads `backend/src/main/resources/db/changelog/db.changelog-master.yaml`. Its changesets create `sample_message` with `hello Rohit`, create `employee`, and invoke `EmployeeSeedChange` to generate and insert 10,000 fictional employees using Datafaker 2.7.0. Liquibase executes the generated insert statements within the seed changeset transaction and manages its connection. Applied changesets are recorded in `DATABASECHANGELOG`; restarting neither duplicates the seed nor overwrites edited rows. Add new changesets for later changes rather than editing applied ones.

Spring Security currently supplies development authentication: username `user` and the generated password printed at startup. Use that password when prompted by curl:

```sh
curl --user user --header 'Accept: application/json' http://localhost:8080/api/sample
```

Expected HTTP 200 body:

```json
{"id":1,"message":"hello Rohit"}
```

This endpoint reads the row through the SQL utility, using Spring JDBC to acquire and release pooled connections. The named HR session endpoints remain part of the application implementation plan.

## Browser console

Restart the backend, then open [http://localhost:8080/h2-console](http://localhost:8080/h2-console). First sign in with Spring's development username `user` and the generated startup password. On the H2 connection form enter:

| Field | Value |
|---|---|
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:~/.acme-salary-management/salary-management;DB_CLOSE_ON_EXIT=FALSE` |
| User Name | `sa` |
| Password | Empty |

Use your configured datasource values if you override the defaults. Click **Connect**, then run:

```sql
SELECT COUNT(*) FROM employee;
SELECT * FROM employee FETCH FIRST 20 ROWS ONLY;
```

The console shares the backend's database process, so the backend stays running while you query. It requires Spring authentication and rejects remote console clients. Console-only security settings allow same-origin frames and exempt its forms from CSRF; API routes retain authentication, CSRF protection, and denied framing.

The local setup enables the console by default. Set `H2_CONSOLE_ENABLED=false` outside local development to disable the console and its custom security configuration.

## Tests and larger datasets

`./mvnw verify` runs tests against a separate in-memory H2 database and temporary file databases. The restart test closes and reopens an application context, verifies saved data survives, and checks the seed has not run twice.

The first database migration automatically generates 10,000 employees across five countries/currencies, six departments, six job levels, and active/inactive statuses. Names come from Datafaker; IDs and fictional `@acme.example` addresses remain unique. Salary amounts use decimals, with joining/end dates and initial salary baselines. Fixed random seeds and a fixed reference date make the dataset reproducible with the pinned library version.

`spring.liquibase.parameters.employeeCount` defaults to `10000`; the test profile uses `20`. Keep this changeset parameter unchanged for an existing database. The file-persistence test explicitly uses 10,000 employees, edits a row, restarts, and verifies that the edit and row count survive without another seed execution.

Existing databases containing the earlier sample changesets receive only the new employee schema/seed on their next run. The sample API remains `GET /api/sample`; employee endpoints are tracked in the README.

The employee schema uses `DECIMAL(19,4)`; generated salary amounts use `BigDecimal`. Validate constraints, reports, transactions, and retry behavior against H2 using the same Liquibase migrations.

References: [H2 features](https://h2database.github.io/html/features.html), [Spring database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html), [Liquibase configuration](https://contribute.liquibase.com/extensions-integrations/directory/integration-docs/springboot/configuration/).

Implementation references: [Datafaker repeatable randomness](https://www.datafaker.net/documentation/usage/), [Liquibase custom changes](https://docs.liquibase.com/community/reference-guide-5-0-2/change-types/customchange).
