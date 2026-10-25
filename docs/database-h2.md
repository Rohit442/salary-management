# H2 database and Liquibase setup

The backend uses embedded, file-based H2. Maven supplies H2 and Liquibase; no database server or container is needed.

## Run and inspect the sample

Use JDK 21 and run `./mvnw spring-boot:run` from `backend/`, or run `SalaryManagementApplication` in IntelliJ. Remove old Oracle `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` overrides from the run configuration.

The default JDBC URL is `jdbc:h2:file:~/.acme-salary-management/salary-management;DB_CLOSE_ON_EXIT=FALSE`. H2 stores the database under your home directory, so starting from the project root or backend folder opens the same database. Restarting the backend retains data. Stop the current backend before launching another instance against this embedded file.

Optional overrides are `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; defaults are the URL above, `sa`, and an empty password. Keep local database files outside source control.

## ER tables and migrations

Liquibase reads `backend/src/main/resources/db/changelog/db.changelog-master.yaml`. The resulting application tables match the [ER diagram](technical-design.md#3-data-model):

| Table | Seed and purpose |
|---|---|
| `HR_USER` | One disabled `seed@acme.example` provenance identity named `Demo Data Seed`; stores a fixed BCrypt hash of an unknown random value, declared in the YAML seed. It is not the Spring development login. |
| `CURRENCY` | `INR`, `USD`, `GBP`, `EUR`, `SGD`, each with two fraction digits |
| `EMPLOYEE` | 10,000 Datafaker records with current salary, immutable initial salary baseline, employment dates, version/timestamps, currency and creation-actor links |
| `SALARY_CHANGE` | Initially empty; holds subsequent changes, actor snapshots, effective dates, resulting versions and retry metadata |

Migrations `005`–`009` add reference tables/data, complete the existing employee table, create salary history, and remove `SAMPLE_MESSAGE`. Applied migrations `001`–`004` are unchanged for compatibility. The original employee seed remains a recorded, one-time Liquibase change; the later schema migration attaches its rows to the currency and provenance records. No employees are copied into another table or regenerated during upgrade.

The employee table enforces unique normalized email/employee IDs, positive amounts, valid date ordering, and creator/currency foreign keys. Field sizes match the employee contract, including 200-character names and job titles. Salary history enforces actor and employee/currency relationships, positive changed amounts, unique actor/retry keys, and unique employee effective dates/resulting versions. Its history index supports employee/time/ID ordering. Foreign keys preserve referenced records rather than cascading deletion.

HR-user and currency seed records are native YAML `insert` changes; their Java custom seeder has been removed. Datafaker employee generation remains in a Liquibase-managed custom change that returns insert statements; Liquibase executes them and manages their transaction.

All changelog files are YAML. Table/column definitions, foreign keys, uniqueness, indexes, updates, and table removal use native Liquibase YAML changes. Check constraints use inline `sql` blocks because the native [`addCheckConstraint` change requires Liquibase Secure](https://docs.liquibase.com/secure/reference-guide-6-0/change-types/addcheckconstraint); the creator backfill uses a computed lookup expression.

The conversion of migrations `005`–`009` retains each original ID, author, and logical file path and accepts its exact recorded checksum, including the previous Java custom seed checksum for `006`. `DATABASECHANGELOG` therefore still shows historical `.sql` logical names, while the actual files and master includes use `.yaml`. Existing migration history and data remain intact without rerunning those changes.

Liquibase owns connection and transaction handling for generated insert statements and tracks completed changes in `DATABASECHANGELOG`. Restarting does not repeat the seed or overwrite saved edits. Add new changesets instead of editing applied ones.

Spring Security's current development login remains username `user` with the generated startup password. The former `/api/sample` greeting endpoint was removed with its hello-world table.

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

`spring.liquibase.parameters.employeeCount` defaults to `10000`; the test profile uses `20`. Keep this changeset parameter unchanged for an existing database. The file-persistence test explicitly creates the old 10,000-employee database, edits a row, upgrades to the ER schema, and restarts again. It verifies that the edit and row count survive with valid creator/currency links and no repeated seed execution.

Existing databases receive the new ER migrations on their next backend startup. Employee API implementation status is tracked in the README.

The employee schema uses `DECIMAL(19,4)`; generated salary amounts use `BigDecimal`. Validate constraints, reports, transactions, and retry behavior against H2 using the same Liquibase migrations.

References: [H2 features](https://h2database.github.io/html/features.html), [Spring database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html), [Liquibase configuration](https://contribute.liquibase.com/extensions-integrations/directory/integration-docs/springboot/configuration/).

Implementation references: [Datafaker repeatable randomness](https://www.datafaker.net/documentation/usage/), [Liquibase custom changes](https://docs.liquibase.com/community/reference-guide-5-0-2/change-types/customchange).
