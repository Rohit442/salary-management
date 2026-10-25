package com.acme.salarymanagement.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:unicode-storage;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UnicodeStorageApiTests {
    private static final String EMAIL="maya.rao@acme.example", BASE="/api/employees", ID="EMP-976000";
    private static final String LETTER=new String(Character.toChars(0x10400));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private ObjectNode createBody() {
        return json.createObjectNode().put("employeeId",ID).put("fullName","Unicode validation")
                .put("workEmail","unicode-validation@acme.example").put("countryCode","IN")
                .put("department","Engineering").put("jobTitle","Engineer").put("jobLevel","L3")
                .put("annualBaseSalary","120000.00").put("currency","INR")
                .put("joiningDate","2024-02-29").put("initialSalaryEffectiveFrom","2024-03-01");
    }

    private void create() throws Exception {
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(createBody().toString()))
                .andExpect(status().isCreated());
    }

    private ObjectNode salaryBody(String reason) {
        return json.createObjectNode().put("annualBaseSalary","130000.00").put("effectiveDate","2024-06-01")
                .put("reason",reason).put("version",0);
    }

    @Test void createPersistsTwoHundredSupplementaryCodePointsForNameAndTitle() throws Exception {
        String text=LETTER.repeat(200);
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(createBody().put("fullName",text).put("jobTitle",text).toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fullName").value(text)).andExpect(jsonPath("$.jobTitle").value(text));
        assertThat(jdbc.queryForObject("SELECT full_name FROM employee WHERE employee_id=?",String.class,ID)).isEqualTo(text);
        assertThat(jdbc.queryForObject("SELECT job_title FROM employee WHERE employee_id=?",String.class,ID)).isEqualTo(text);
    }

    @Test void editPersistsTwoHundredSupplementaryCodePointsWithoutChangingTheSalaryBaseline() throws Exception {
        create();
        String text=LETTER.repeat(200);
        ObjectNode body=createBody().put("fullName",text).put("jobTitle",text).putNull("lastEmploymentDate").put("version",0);
        body.remove(java.util.List.of("employeeId","annualBaseSalary","currency","initialSalaryEffectiveFrom"));
        mvc.perform(put(BASE+"/"+ID+"/details").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value(text)).andExpect(jsonPath("$.jobTitle").value(text))
                .andExpect(jsonPath("$.annualBaseSalary").value("120000.00")).andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("SELECT full_name FROM employee WHERE employee_id=?",String.class,ID)).isEqualTo(text);
        assertThat(jdbc.queryForObject("SELECT job_title FROM employee WHERE employee_id=?",String.class,ID)).isEqualTo(text);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change",Integer.class)).isZero();
    }

    @Test void salaryPersistsOneThousandSupplementaryCodePointsAndReplaysTheSameHistory() throws Exception {
        create();
        String reason=LETTER.repeat(1000), key=UUID.randomUUID().toString(), path=BASE+"/"+ID+"/salary-changes";
        for (boolean replayed : new boolean[]{false,true}) {
            mvc.perform(post(path).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).header("Idempotency-Key",key)
                    .contentType(MediaType.APPLICATION_JSON).content(salaryBody(reason).toString()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.reason").value(reason))
                    .andExpect(header().string("Idempotency-Replayed",Boolean.toString(replayed)));
        }
        assertThat(jdbc.queryForObject("SELECT reason FROM salary_change",String.class)).isEqualTo(reason);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM employee WHERE employee_id=?",Long.class,ID)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"fullName,false","fullName,true","jobTitle,false","jobTitle,true"})
    void createStillRejectsMoreThanTwoHundredCodePoints(String field,boolean supplementary) throws Exception {
        String text=(supplementary?LETTER:"A").repeat(201);
        mvc.perform(post(BASE).with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(createBody().put(field,text).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field)).andExpect(jsonPath("$.fieldErrors[0].code").value("TOO_LONG"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE employee_id=?",Integer.class,ID)).isZero();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void salaryStillRejectsMoreThanOneThousandCodePointsWithoutWritingHistory(boolean supplementary) throws Exception {
        create();
        String reason=(supplementary?LETTER:"A").repeat(1001);
        mvc.perform(post(BASE+"/"+ID+"/salary-changes").with(user(EMAIL).roles("HR")).with(csrf().asHeader())
                .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(salaryBody(reason).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason")).andExpect(jsonPath("$.fieldErrors[0].code").value("TOO_LONG"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM employee WHERE employee_id=?",Long.class,ID)).isZero();
    }
}
