--liquibase formatted sql

--changeset salary-management:007-add-employee-creator
ALTER TABLE employee ADD created_by_fk BIGINT;
ALTER TABLE employee ADD created_by_display_name VARCHAR(200);

--changeset salary-management:007-backfill-employee-creator
UPDATE employee
SET created_by_fk = (SELECT id FROM hr_user WHERE work_email = 'seed@acme.example'),
    created_by_display_name = 'Demo Data Seed'
WHERE created_by_fk IS NULL;

--changeset salary-management:007-enforce-employee-relationships
ALTER TABLE employee ALTER COLUMN created_by_fk SET NOT NULL;
ALTER TABLE employee ALTER COLUMN created_by_display_name SET NOT NULL;
ALTER TABLE employee ALTER COLUMN full_name VARCHAR(200);
ALTER TABLE employee ALTER COLUMN job_title VARCHAR(200);
ALTER TABLE employee ALTER COLUMN job_level VARCHAR(20);
ALTER TABLE employee ADD CONSTRAINT fk_employee_creator FOREIGN KEY (created_by_fk) REFERENCES hr_user(id);
ALTER TABLE employee ADD CONSTRAINT fk_employee_currency FOREIGN KEY (currency_code) REFERENCES currency(code);
ALTER TABLE employee ADD CONSTRAINT uq_employee_id_currency UNIQUE (id, currency_code);
ALTER TABLE employee ADD CONSTRAINT ck_employee_email CHECK (work_email = LOWER(TRIM(work_email)) AND CHAR_LENGTH(work_email) > 0);
ALTER TABLE employee ADD CONSTRAINT ck_employee_identity CHECK (REGEXP_LIKE(employee_id, '^EMP-[0-9]{6}$'));
ALTER TABLE employee ADD CONSTRAINT ck_employee_name CHECK (CHAR_LENGTH(TRIM(full_name)) > 0);
ALTER TABLE employee ADD CONSTRAINT ck_employee_title CHECK (CHAR_LENGTH(TRIM(job_title)) > 0);
ALTER TABLE employee ADD CONSTRAINT ck_employee_creator_name CHECK (CHAR_LENGTH(TRIM(created_by_display_name)) > 0);
ALTER TABLE employee ADD CONSTRAINT ck_employee_effective_dates CHECK (
    initial_salary_effective_from >= joining_date
    AND salary_effective_from >= initial_salary_effective_from
    AND (last_employment_date IS NULL OR salary_effective_from <= last_employment_date)
);
ALTER TABLE employee ADD CONSTRAINT ck_employee_version_safe CHECK (version <= 9007199254740991);
