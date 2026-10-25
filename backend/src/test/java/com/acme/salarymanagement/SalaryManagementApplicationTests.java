package com.acme.salarymanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalaryManagementApplicationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void erTablesAndEmployeeRelationshipsArePresent() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'", String.class))
                .contains("HR_USER", "CURRENCY", "EMPLOYEE", "SALARY_CHANGE")
                .doesNotContain("SAMPLE_MESSAGE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM currency", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee e JOIN currency c ON c.code = e.currency_code JOIN hr_user h ON h.id = e.created_by_fk WHERE e.created_by_display_name = h.display_name AND h.enabled = FALSE", Integer.class))
                .isEqualTo(20);
    }

    @Test
    void seedsConfiguredEmployeeFixtureWithUniqueIdentitiesAndValidAmounts() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT employee_id) FROM employee", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT work_email) FROM employee", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT country_code) FROM employee", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT currency_code) FROM employee", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE annual_base_salary <= 0 OR initial_annual_base_salary <> annual_base_salary OR initial_salary_effective_from <> joining_date OR salary_effective_from <> joining_date", Integer.class)).isZero();
    }

    @Test
    @Transactional
    void employeeRelationshipsAndStorageLimitsMatchTheContract() {
        assertThatThrownBy(() -> jdbc.update("UPDATE employee SET currency_code = 'ZZZ' WHERE employee_id = 'EMP-000001'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE employee SET created_by_fk = -1 WHERE employee_id = 'EMP-000001'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE employee SET annual_base_salary = 0 WHERE employee_id = 'EMP-000001'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE employee SET work_email = 'UPPER@acme.example' WHERE employee_id = 'EMP-000001'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("UPDATE employee SET full_name = ?, job_title = ? WHERE employee_id = ?",
                "N".repeat(200), "T".repeat(200), "EMP-000001")).isEqualTo(1);
    }

    @Test
    @Transactional
    void salaryHistoryEnforcesReferencesReplayUniquenessAndAmounts() {
        String key = "11111111-1111-4111-8111-111111111111";
        assertThat(insertChange("EMP-000001", "INR", key, 1, "2026-02-01")).isEqualTo(1);
        assertThatThrownBy(() -> insertChange("EMP-000002", "USD", key, 1, "2026-02-02"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("UQ_SALARY_CHANGE_ACTOR_REQUEST");
        assertThatThrownBy(() -> insertChange("EMP-000001", "USD",
                "22222222-2222-4222-8222-222222222222", 2, "2026-02-02"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("FK_SALARY_CHANGE_EMPLOYEE_CURRENCY");
        assertThatThrownBy(() -> jdbc.update("UPDATE salary_change SET actor_fk = -1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE salary_change SET new_amount = previous_amount"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE salary_change SET previous_amount = 0"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE salary_change SET reason = '   '"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange("EMP-000001", "INR",
                "33333333-3333-4333-8333-333333333333", 2, "2026-02-01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("UQ_SALARY_CHANGE_EMPLOYEE_EFFECTIVE");
    }

    private int insertChange(String employeeId, String currency, String requestId, long version, String effectiveDate) {
        return jdbc.update("""
                INSERT INTO salary_change (employee_fk, actor_fk, actor_display_name,
                    previous_amount, new_amount, currency_code, reason, effective_date,
                    changed_at, resulting_version, request_id, request_fingerprint)
                SELECT id, created_by_fk, created_by_display_name,
                    annual_base_salary, annual_base_salary + 100, ?, 'Review', ?, ?, ?, ?, ?
                FROM employee WHERE employee_id = ?
                """, currency, LocalDate.parse(effectiveDate), OffsetDateTime.parse("2026-02-01T00:00:00Z"),
                version, requestId, "a".repeat(64), employeeId);
    }

    @Test
    @WithMockUser
    void apiRejectsWriteWithoutCsrfToken() throws Exception {
        mvc.perform(post("/api/employees")).andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/employees").accept("application/json"))
                .andExpect(status().isUnauthorized());
    }
}
