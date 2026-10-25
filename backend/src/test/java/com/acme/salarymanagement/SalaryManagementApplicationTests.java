package com.acme.salarymanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
    @WithMockUser
    void returnsGreetingReadFromSeededTable() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample_message", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(1))
                .andExpect(jsonPath("message").value("hello Rohit"));
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
    @WithMockUser
    void apiRejectsWriteWithoutCsrfToken() throws Exception {
        mvc.perform(post("/api/sample")).andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/sample").accept("application/json"))
                .andExpect(status().isUnauthorized());
    }
}
