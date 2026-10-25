package com.acme.salarymanagement.rest;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:base-pay-api;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class BasePayApiTests {
    static final String ROOT="/api/employees/EMP-000001", PATH=ROOT+"/base-pay-estimate", EMAIL="maya.rao@acme.example";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void fixture() throws Exception {
        jdbc.update("UPDATE employee SET joining_date='2026-04-01',last_employment_date='2026-11-12',initial_salary_effective_from='2026-04-01',salary_effective_from='2026-04-01',annual_base_salary=1200000,initial_annual_base_salary=1200000 WHERE employee_id='EMP-000001'");
        mvc.perform(post(ROOT+"/salary-changes").with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"annualBaseSalary\":\"1800000\",\"reason\":\"Review\",\"effectiveDate\":\"2026-10-15\",\"version\":0}"))
                .andExpect(status().isCreated());
    }
    @ParameterizedTest @CsvSource({"2026-09,30,100000.00,INITIAL","2026-10,31,150000.00,SALARY_CHANGE","2026-11,12,60000.00,SALARY_CHANGE"})
    void appliesWholeMonthRaiseAndInclusiveLeavingDate(String month,int days,String pay,String source) throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month",month)).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleDays").value(days)).andExpect(jsonPath("$.estimatedBasePay").value(pay))
                .andExpect(jsonPath("$.salaryBasis.source").value(source)).andExpect(jsonPath("$.employeeVersion").value(1))
                .andExpect(jsonPath("$.prorationBasis").value("CALENDAR_DAYS"));
    }
    @Test void noEligibleDaysReturnNullBasisAndInactiveRecordsRemainEligible() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-12")).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleDays").value(0)).andExpect(jsonPath("$.salaryBasis").isEmpty())
                .andExpect(jsonPath("$.estimatedBasePay").value("0.00"));
        jdbc.update("UPDATE employee SET employment_status='INACTIVE' WHERE employee_id='EMP-000001'");
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-11")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBasePay").value("60000.00"));
    }
    @Test void latestChangeWithinMonthWinsForEntireMonth() throws Exception {
        mvc.perform(post(ROOT+"/salary-changes").with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"annualBaseSalary\":\"2400000\",\"reason\":\"Review two\",\"effectiveDate\":\"2026-10-20\",\"version\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBasePay").value("200000.00")).andExpect(jsonPath("$.salaryBasis.effectiveDate").value("2026-10-20"));
    }
    @Test void coverageGapsAreConflictsOnlyWhenEmploymentOverlaps() throws Exception {
        jdbc.update("UPDATE employee SET initial_salary_effective_from='2026-05-01' WHERE employee_id='EMP-000001'");
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-04")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALARY_HISTORY_INCOMPLETE"));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-03")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBasePay").value("0.00"));
    }
    @Test void roundsFromAnnualAmountRatherThanRoundedMonthlyDisplay() throws Exception {
        jdbc.update("UPDATE employee SET joining_date='2026-01-15',initial_salary_effective_from='2026-01-15',initial_annual_base_salary=1 WHERE employee_id='EMP-000001'");
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-01")).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleDays").value(17)).andExpect(jsonPath("$.salaryBasis.monthlyBasePay").value("0.08"))
                .andExpect(jsonPath("$.estimatedBasePay").value("0.05"));
    }
    @ParameterizedTest @ValueSource(strings={"", "2026-00", "2026-13", "2026-1", "0000-01", "10000-01", "2026-01-01"})
    void rejectsInvalidMonths(String month) throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month",month)).andExpect(status().isBadRequest());
    }
    @Test void validatesMissingRepeatedUnknownParametersAndAuthorization() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR"))).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-01","2026-02")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-01").queryParam("q","x")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).queryParam("month","2026-01")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("month","2026-01").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
