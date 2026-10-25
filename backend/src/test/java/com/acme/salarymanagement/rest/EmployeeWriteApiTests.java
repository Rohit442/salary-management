package com.acme.salarymanagement.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
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

@SpringBootTest(properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:employee-writes;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeWriteApiTests {
    static final String EMAIL="maya.rao@acme.example", BASE="/api/employees", ID="EMP-010001";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    String createBody() { return """
            {"employeeId":"EMP-010001","fullName":" Ananya Iyer ","workEmail":" ANANYA@ACME.EXAMPLE ",
             "countryCode":"IN","department":"Engineering","jobTitle":"Engineer","jobLevel":"L3",
             "annualBaseSalary":"960000.00","currency":"INR","joiningDate":"2026-01-01","initialSalaryEffectiveFrom":"2026-01-01"}
            """; }
    ObjectNode details() { return json.createObjectNode().put("fullName","Ananya Iyer").put("workEmail","ananya@acme.example")
            .put("countryCode","IN").put("department","Product").put("jobTitle","Manager").put("jobLevel","L4")
            .put("joiningDate","2026-01-01").putNull("lastEmploymentDate").put("version",0); }
    void create() throws Exception {
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(createBody())).andExpect(status().isCreated());
    }
    @Test void createsBaselineAndActorWithoutCreatingHistory() throws Exception {
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isCreated()).andExpect(header().string("Location",BASE+"/"+ID))
                .andExpect(jsonPath("$.fullName").value("Ananya Iyer")).andExpect(jsonPath("$.workEmail").value("ananya@acme.example"))
                .andExpect(jsonPath("$.annualBaseSalary").value("960000.00")).andExpect(jsonPath("$.version").value(0));
        assertThat(jdbc.queryForObject("SELECT created_by_display_name FROM employee WHERE employee_id=?",String.class,ID)).isEqualTo("Maya Rao");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change",Integer.class)).isZero();
    }
    @Test void duplicateIdsAndNormalizedEmailsAreConflicts() throws Exception {
        create();
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPLOYEE_ID_EXISTS"));
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody().replace(ID,"EMP-010002")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORK_EMAIL_EXISTS"));
    }
    @Test void detailsPreserveBaselineAndSupportNoOpsAndVersionConflicts() throws Exception {
        create();
        var first=mvc.perform(put(BASE+"/"+ID+"/details").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(details().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.annualBaseSalary").value("960000.00")).andReturn();
        var body=details().put("version",1);
        var noOp=mvc.perform(put(BASE+"/"+ID+"/details").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn();
        assertThat(json.readTree(noOp.getResponse().getContentAsString()).get("updatedAt"))
                .isEqualTo(json.readTree(first.getResponse().getContentAsString()).get("updatedAt"));
        mvc.perform(put(BASE+"/"+ID+"/details").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(details().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.details.expectedVersion").value(0)).andExpect(jsonPath("$.details.currentVersion").value(1));
    }
    @Test void inactivationIsVersionedAndRepeatWithCurrentVersionIsNoOp() throws Exception {
        create();
        for (int version : List.of(0,1)) {
            mvc.perform(put(BASE+"/"+ID+"/status").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"employmentStatus\":\"INACTIVE\",\"version\":"+version+"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.employmentStatus").value("INACTIVE"));
        }
        mvc.perform(put(BASE+"/"+ID+"/status").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employmentStatus\":\"INACTIVE\",\"version\":0}"))
                .andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"annualBaseSalary","currency","version","initialSalaryEffectiveFrom","joiningDate","fullName","workEmail","countryCode"})
    void invalidCreateLeavesNoPartialRecord(String field) throws Exception {
        var body=(ObjectNode)json.readTree(createBody());
        switch(field) {
            case "annualBaseSalary" -> body.put(field,960000);
            case "currency" -> body.put(field,"ABC");
            case "version" -> body.put(field,0);
            case "initialSalaryEffectiveFrom" -> body.put(field,"2025-12-31");
            case "joiningDate" -> body.put(field,"9999-12-31");
            case "fullName" -> body.put(field," ");
            case "workEmail" -> body.put(field,"invalid");
            default -> body.put(field,"in");
        }
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE employee_id=?",Integer.class,ID)).isZero();
    }
    @Test void rejectsDuplicateJsonUnknownMissingAndContradictoryDetails() throws Exception {
        create();
        for(String body: List.of(details().put("annualBaseSalary","1.00").toString(),
                details().put("joiningDate","2026-02-01").toString(), details().put("lastEmploymentDate","2025-01-01").toString(),
                details().put("version",0.1).toString(), details().put("version",9007199254740992L).toString(),
                "{\"version\":0,\"version\":1}")) {
            mvc.perform(put(BASE+"/"+ID+"/details").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT version FROM employee WHERE employee_id=?",Long.class,ID)).isZero();
    }
    @Test void securityMediaAndLimitsApplyBeforeWrites() throws Exception {
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).contentType(MediaType.APPLICATION_JSON).content(createBody())).andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(createBody())).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).with(user(EMAIL).roles("USER")).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(createBody())).andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).contentType(MediaType.TEXT_PLAIN).content(createBody())).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content("x".repeat(16385))).andExpect(status().isPayloadTooLarge());
        mvc.perform(put(BASE+"/EMP-999999/status").with(user(EMAIL).roles("HR")).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employmentStatus\":\"INACTIVE\",\"version\":0}")).andExpect(status().isNotFound());
    }
}
