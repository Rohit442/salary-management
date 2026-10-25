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
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/sample").accept("application/json"))
                .andExpect(status().isUnauthorized());
    }
}
