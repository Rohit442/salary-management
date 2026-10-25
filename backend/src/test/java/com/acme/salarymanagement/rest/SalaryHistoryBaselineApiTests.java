package com.acme.salarymanagement.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.h2.console.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:salary-history-baseline-api;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SalaryHistoryBaselineApiTests {
    private static final String PATH="/api/employees/EMP-000001/salary-changes", EMAIL="maya.rao@acme.example";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private Map<String,Object> expected;

    @BeforeEach void baseline() {
        jdbc.update("""
                UPDATE employee SET joining_date='2026-04-01',last_employment_date=NULL,
                initial_salary_effective_from='2026-04-05',salary_effective_from='2026-04-05',
                annual_base_salary=1200000.50,initial_annual_base_salary=1200000.50,
                created_at='2026-04-10T03:45:00Z',created_by_display_name='Original creator',employment_status='ACTIVE'
                WHERE employee_id='EMP-000001'
                """);
        jdbc.update("UPDATE hr_user SET display_name='Renamed creator' WHERE work_email='seed@acme.example'");
        String actorId=jdbc.queryForObject("SELECT public_id FROM hr_user WHERE work_email='seed@acme.example'",String.class);
        expected=Map.of("annualBaseSalary","1200000.50","currency","INR","effectiveDate","2026-04-05",
                "recordedAt","2026-04-10T03:45:00Z","actor",Map.of("id",actorId,"displayName","Original creator"));
    }

    @ParameterizedTest @ValueSource(strings={"ACTIVE","INACTIVE"})
    void emptyHistoryIncludesTheStoredBaselineAndCreatorSnapshot(String status) throws Exception {
        jdbc.update("UPDATE employee SET employment_status=? WHERE employee_id='EMP-000001'",status);
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items",hasSize(0)))
                .andExpect(jsonPath("$.initialSalary",equalTo(expected)))
                .andExpect(jsonPath("$.initialSalary",aMapWithSize(5)))
                .andExpect(jsonPath("$.initialSalary.actor",aMapWithSize(2)));
    }

    @Test void baselineRemainsUnchangedAfterSalaryWritesOnEveryHistoryPage() throws Exception {
        change("1452000.00","2026-10-15",0);
        change("1800000.00","2026-10-20",1);
        jdbc.update("UPDATE employee SET employment_status='INACTIVE' WHERE employee_id='EMP-000001'");
        for (int page=0;page<3;page++) {
            mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("page",String.valueOf(page)).queryParam("size","1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.items",hasSize(page<2?1:0)))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.initialSalary",equalTo(expected)));
        }
        mvc.perform(get("/api/employees/EMP-000001").with(user(EMAIL).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.annualBaseSalary").value("1800000.00"));
    }

    @Test void createdEmployeeBaselineUsesItsCreationTimeAndOriginalHrName() throws Exception {
        var created=mvc.perform(post("/api/employees").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"employeeId":"EMP-010001","fullName":"Baseline Example","workEmail":"baseline@example.com",
                 "countryCode":"GB","department":"Engineering","jobTitle":"Engineer","jobLevel":"L4",
                 "annualBaseSalary":"12345.67","currency":"GBP","joiningDate":"2026-04-01","initialSalaryEffectiveFrom":"2026-04-03"}
                """)).andExpect(status().isCreated()).andReturn();
        String recordedAt=json.readTree(created.getResponse().getContentAsString()).get("createdAt").asText();
        String actorId=jdbc.queryForObject("SELECT public_id FROM hr_user WHERE work_email=?",String.class,EMAIL);
        jdbc.update("UPDATE hr_user SET display_name='New HR name' WHERE work_email=?",EMAIL);
        mvc.perform(get("/api/employees/EMP-010001/salary-changes").with(user(EMAIL).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.initialSalary",equalTo(Map.of(
                        "annualBaseSalary","12345.67","currency","GBP","effectiveDate","2026-04-03",
                        "recordedAt",recordedAt,"actor",Map.of("id",actorId,"displayName","Maya Rao")))));
    }

    private void change(String amount,String effectiveDate,long version) throws Exception {
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"annualBaseSalary":"%s","effectiveDate":"%s","reason":"Review","version":%d}
                """.formatted(amount,effectiveDate,version)))
                .andExpect(status().isCreated());
    }
}
