package com.acme.salarymanagement.rest;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:salary-api;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SalaryApiTests {
    static final String PATH="/api/employees/EMP-000001/salary-changes", EMAIL="maya.rao@acme.example";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    String body(String amount,long version) { return "{\"annualBaseSalary\":\""+amount+"\",\"reason\":\" Review \",\"effectiveDate\":\"2026-01-15\",\"version\":"+version+"}"; }
    @Test void changeHistoryAndEquivalentReplayAreAtomicAndStable() throws Exception {
        String key=UUID.randomUUID().toString();
        var first=mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body("900000",0)))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed","false"))
                .andExpect(jsonPath("$.newAmount").value("900000.00")).andExpect(jsonPath("$.reason").value("Review"))
                .andExpect(jsonPath("$.resultingEmployeeVersion").value(1)).andReturn();
        jdbc.update("UPDATE employee SET employment_status='INACTIVE' WHERE employee_id='EMP-000001'");
        var replay=mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body("900000.00",0)))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed","true")).andReturn();
        assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(jdbc.queryForObject("SELECT version FROM employee WHERE employee_id='EMP-000001'",Long.class)).isEqualTo(1);
    }
    @Test void keyReuseWithChangedIntentIsConflict() throws Exception {
        String key=UUID.randomUUID().toString();
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body("900000",0))).andExpect(status().isCreated());
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(body("910000",1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1e6","1,000","900000.001","9999999999999999"})
    void invalidAmountsDoNotChangeEmployeeOrHistory(String amount) throws Exception {
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body(amount,0))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM employee WHERE employee_id='EMP-000001'",Long.class)).isZero();
    }
    @Test void unchangedSalaryStaleVersionAndInactiveAreRejected() throws Exception {
        String amount=jdbc.queryForObject("SELECT annual_base_salary FROM employee WHERE employee_id='EMP-000001'",java.math.BigDecimal.class).setScale(2).toPlainString();
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body(amount,0))).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body("900000",4))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        jdbc.update("UPDATE employee SET employment_status='INACTIVE' WHERE employee_id='EMP-000001'");
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body("900000",0))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPLOYEE_INACTIVE"));
    }
    @Test void validatesKeyDatesHistoryParametersAndSecurity() throws Exception {
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(body("900000",0))).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body("900000",0).replace("2026-01-15","9999-12-31"))).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("size","0")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("sort","changedAt,asc")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("page","1000000")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).contentType(MediaType.APPLICATION_JSON).content(body("900000",0))).andExpect(status().isForbidden());
    }
}
