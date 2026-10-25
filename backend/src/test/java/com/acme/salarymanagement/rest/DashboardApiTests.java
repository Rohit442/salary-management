package com.acme.salarymanagement.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.h2.console.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:dashboard-api;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardApiTests {
    private static final String REPORT = "/api/reports/compensation";
    private static final String EMAIL = "maya.rao@acme.example";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void fixture() {
        jdbc.update("UPDATE employee SET employment_status='INACTIVE'");
        String[] amounts = {"1320000", "1440000", "1560000", "1200000", "1680000", "135000", "145000"};
        for (int i = 0; i < amounts.length; i++) {
            jdbc.update("UPDATE employee SET employment_status='ACTIVE', annual_base_salary=?, currency_code=?, "
                    + "country_code=?, department=?, job_level='L4' WHERE employee_id=?",
                    new java.math.BigDecimal(amounts[i]), i < 5 ? "INR" : "USD", i < 5 ? "IN" : "US",
                    i < 3 ? "Engineering" : "Product", String.format("EMP-%06d", i + 1));
        }
    }

    @Test
    void returnsIndependentCurrencyMetricsAndMetadata() throws Exception {
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR")))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.totalEmployees").value(7))
                .andExpect(jsonPath("$.groupBy", contains("currency")))
                .andExpect(jsonPath("$.currencySummaries[0].currency").value("INR"))
                .andExpect(jsonPath("$.currencySummaries[0].totalAnnualBaseSalary").value("7200000.00"))
                .andExpect(jsonPath("$.currencySummaries[0].averageAnnualBaseSalary").value("1440000.00"))
                .andExpect(jsonPath("$.currencySummaries[0].medianAnnualBaseSalary").value("1440000.00"))
                .andExpect(jsonPath("$.currencySummaries[1].medianAnnualBaseSalary").value("140000.00"))
                .andExpect(jsonPath("$.generatedAt").isString());
    }

    @ParameterizedTest
    @ValueSource(ints = {0,1,2,3,4,5,6,7})
    void allGroupingCombinationsPreserveCountsAndOverallMetrics(int mask) throws Exception {
        var request = get(REPORT).with(user(EMAIL).roles("HR"));
        var dimensions = List.of("country", "department", "jobLevel");
        for (int i = 2; i >= 0; i--) if ((mask & (1 << i)) != 0) request.queryParam("groupBy", dimensions.get(i));
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        int count = 0;
        for (var group : body.get("groups")) {
            count += group.get("employeeCount").asInt();
            assertThat(group.get("dimensions").size()).isEqualTo(Integer.bitCount(mask) + 1);
        }
        assertThat(count).isEqualTo(7);
        assertThat(body.at("/currencySummaries/0/medianAnnualBaseSalary").asText()).isEqualTo("1440000.00");
    }

    @Test
    void filtersUseOrWithinAndAcrossCategoriesAndDeduplicate() throws Exception {
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR"))
                        .queryParam("country", "US", "IN", "IN").queryParam("department", "Engineering")
                        .queryParam("jobLevel", "L4").queryParam("currency", "INR")
                        .queryParam("groupBy", "department", "department"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalEmployees").value(3))
                .andExpect(jsonPath("$.appliedFilters.country", contains("IN", "US")))
                .andExpect(jsonPath("$.groupBy", contains("department", "currency")));
    }

    @Test
    void noMatchesProducesEmptyArrays() throws Exception {
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR")).queryParam("jobLevel", "L6"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalEmployees").value(0))
                .andExpect(jsonPath("$.groups", hasSize(0))).andExpect(jsonPath("$.currencySummaries", hasSize(0)));
    }

    @Test
    void roundsOnceAndHandlesTotalsAboveEmployeePrecision() throws Exception {
        jdbc.update("UPDATE employee SET annual_base_salary=999999999999999.9999 WHERE currency_code='INR'");
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencySummaries[0].totalAnnualBaseSalary").value("5000000000000000.00"));
        jdbc.update("UPDATE employee SET annual_base_salary=1.0049 WHERE employee_id='EMP-000006'");
        jdbc.update("UPDATE employee SET annual_base_salary=1.0050 WHERE employee_id='EMP-000007'");
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR")).queryParam("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencySummaries[0].totalAnnualBaseSalary").value("2.01"))
                .andExpect(jsonPath("$.currencySummaries[0].averageAnnualBaseSalary").value("1.00"))
                .andExpect(jsonPath("$.currencySummaries[0].medianAnnualBaseSalary").value("1.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"country=in", "country=IN,US", "country=", "groupBy=currency", "groupBy=unknown", "page=0", "department=engineering"})
    void rejectsUnsupportedFilters(String query) throws Exception {
        String[] parts = query.split("=", -1);
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR")).queryParam(parts[0], parts[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsTooManyValuesBeforeDeduplication() throws Exception {
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR"))
                        .queryParam("country", java.util.Collections.nCopies(21, "IN").toArray(String[]::new)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void referencesMatchCanonicalVocabularyAndPrecision() throws Exception {
        mvc.perform(get("/api/reference-data").with(user(EMAIL).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.countries[*].code", contains("DE","GB","IN","SG","US")))
                .andExpect(jsonPath("$.departments[*].value", contains("Design","Engineering","Finance","HR","Operations","Product")))
                .andExpect(jsonPath("$.jobLevels[*].value", contains("L1","L2","L3","L4","L5","L6")))
                .andExpect(jsonPath("$.currencies[*].code", contains("EUR","GBP","INR","SGD","USD")))
                .andExpect(jsonPath("$.currencies[*].fractionDigits", everyItem(is(2))));
    }

    @Test
    void protectsDashboardAndRejectsRevokedAccounts() throws Exception {
        for (String path : List.of(REPORT, "/api/reference-data")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            mvc.perform(get(path).with(user(EMAIL).roles("USER"))).andExpect(status().isForbidden());
            jdbc.update("UPDATE hr_user SET enabled=false WHERE work_email=?", EMAIL);
            mvc.perform(get(path).with(user(EMAIL).roles("HR"))).andExpect(status().isUnauthorized());
            jdbc.update("UPDATE hr_user SET enabled=true WHERE work_email=?", EMAIL);
        }
    }

    @Test
    void onlyGetIsSupported() throws Exception {
        for (String path : List.of(REPORT, "/api/reference-data")) {
            mvc.perform(head(path).with(user(EMAIL).roles("HR")))
                    .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"));
            mvc.perform(post(path).with(user(EMAIL).roles("HR")).with(csrf().asHeader()))
                    .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }
    }

    @Test
    void rejectsBodiesUnknownReferenceParametersAndNonJsonResponses() throws Exception {
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR")).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get(REPORT).with(user(EMAIL).roles("HR")).accept("text/html"))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
        mvc.perform(get("/api/reference-data").with(user(EMAIL).roles("HR")).queryParam("q", "x"))
                .andExpect(status().isBadRequest());
    }
}
