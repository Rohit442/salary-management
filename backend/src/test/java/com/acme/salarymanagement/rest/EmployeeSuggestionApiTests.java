package com.acme.salarymanagement.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.h2.console.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:employee-suggestion-api;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeSuggestionApiTests {
    private static final String PATH = "/api/employees/suggestions";
    private static final String EMAIL = "maya.rao@acme.example";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        jdbc.update("UPDATE employee SET full_name='Other person'");
        name(1, "Ravi Shah");
        name(2, "rAVI Shah");
        name(3, "Raquel Jones");
        name(4, "Priya Rao");
        name(5, "Arjun Mehta");
        name(6, "Rohit Singh");
        name(11, "Rita Lee");
    }

    @Test
    void returnsOnlyNamesAndIdsForCaseInsensitiveLiteralNamePrefixesInStableOrder() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", " r "))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].employeeId", contains("EMP-000003", "EMP-000001", "EMP-000002", "EMP-000011", "EMP-000006")))
                .andExpect(jsonPath("$[*].fullName", contains("Raquel Jones", "Ravi Shah", "rAVI Shah", "Rita Lee", "Rohit Singh")))
                .andExpect(jsonPath("$[0]", aMapWithSize(2)))
                .andExpect(jsonPath("$[3]", aMapWithSize(2)));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "Ra"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].employeeId", contains("EMP-000003", "EMP-000001", "EMP-000002")));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "EMP-000001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void defaultsToEightSuggestionsAndHonorsOneAndTenWithoutChangingDirectoryDefaults() throws Exception {
        jdbc.update("UPDATE employee SET full_name='Robert Smith'");
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].employeeId").value("EMP-000001"))
                .andExpect(jsonPath("$[7].employeeId").value("EMP-000008"));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R").queryParam("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R").queryParam("limit", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[9].employeeId").value("EMP-000010"));
        mvc.perform(get("/api/employees").with(user(EMAIL).roles("HR")).queryParam("q", "bert"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(20))
                .andExpect(jsonPath("$.size").value(25)).andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void escapesPatternCharactersAndReturnsAnEmptyArrayForNoMatch() throws Exception {
        name(1, "100%_!Example");
        name(2, "100ABCX!Example");
        name(3, "100%_Other");
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "100%_!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].employeeId", contains("EMP-000001")));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "Nobody"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void includesInactiveEmployeesAndDoesNotFoldAccents() throws Exception {
        name(11, "Élodie Martin");
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "é"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].employeeId", contains("EMP-000011")));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "e"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void appliesEveryCategoryAndThePrefixBeforeLimitingSuggestions() throws Exception {
        jdbc.update("UPDATE employee SET full_name='Other person'");
        name(1, "R 01 country mismatch");
        categories(1, "US", "Engineering", "L1", "INR");
        name(2, "R 02 department mismatch");
        categories(2, "IN", "Finance", "L1", "INR");
        name(3, "R 03 level mismatch");
        categories(3, "IN", "Engineering", "L2", "INR");
        name(4, "R 04 currency mismatch");
        categories(4, "IN", "Engineering", "L1", "USD");
        name(11, "R 99 matching inactive");
        categories(11, "IN", "Engineering", "L1", "INR");
        name(12, "Arjun matching categories");
        categories(12, "IN", "Engineering", "L1", "INR");

        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R").queryParam("limit", "1")
                        .queryParam("country", "IN").queryParam("department", "Engineering")
                        .queryParam("jobLevel", "L1").queryParam("currency", "INR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].employeeId", contains("EMP-000011")))
                .andExpect(jsonPath("$[0]", aMapWithSize(2)));
    }

    @Test
    void repeatedCategoryValuesUseOrWithoutDuplicatingSuggestions() throws Exception {
        jdbc.update("UPDATE employee SET full_name='Other person'");
        name(1, "Ravi Shah");
        categories(1, "IN", "Engineering", "L1", "INR");
        name(2, "Raquel Jones");
        categories(2, "US", "Finance", "L2", "USD");
        name(3, "Rohit Singh");
        categories(3, "GB", "Engineering", "L1", "GBP");
        name(11, "Rita Lee");
        categories(11, "IN", "Finance", "L2", "USD");

        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R")
                        .queryParam("country", "IN", "US", "IN")
                        .queryParam("department", "Engineering", "Finance", "Engineering")
                        .queryParam("jobLevel", "L1", "L2").queryParam("currency", "INR", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].employeeId", contains("EMP-000002", "EMP-000001", "EMP-000011")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"country=", "country=in", "country=XX", "country=IN,US",
            "department=engineering", "department=Unlisted", "jobLevel=l1", "currency=inr"})
    void rejectsNonCanonicalFilterValues(String query) throws Exception {
        String[] pair = query.split("=", -1);
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R").queryParam(pair[0], pair[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(pair[0]))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("UNSUPPORTED_VALUE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"country=IN", "department=Engineering", "jobLevel=L1", "currency=INR"})
    void capsSuppliedValuesPerCategoryBeforeDeduplication(String query) throws Exception {
        String[] pair = query.split("=", -1);
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R")
                        .queryParam(pair[0], java.util.Collections.nCopies(20, pair[1]).toArray(String[]::new)))
                .andExpect(status().isOk());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R")
                        .queryParam(pair[0], java.util.Collections.nCopies(21, pair[1]).toArray(String[]::new)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value(pair[0]))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("TOO_MANY_VALUES"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prefix=", "prefix=   ", "limit=0", "limit=11", "limit=-1", "limit=1.5",
            "limit=9999999999999999999999", "limit=", "q=R", "futureFilter=true", "page=0", "sort=fullName,asc"})
    void rejectsInvalidOrUnknownParameters(String query) throws Exception {
        String[] pair = query.split("=", -1);
        var request = get(PATH).with(user(EMAIL).roles("HR"));
        if (!pair[0].equals("prefix")) request.queryParam("prefix", "R");
        mvc.perform(request.queryParam(pair[0], pair[1])).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(pair[0])));
    }

    @Test
    void requiresOnePrefixAndRejectsRepeatedScalarsAndOverlongText() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("prefix")));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R", "Ra"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].code", hasItem("DUPLICATE")));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R").queryParam("limit", "1", "2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].code", hasItem("DUPLICATE")));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R".repeat(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("prefix")));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R".repeat(100)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "\uD83D\uDE00".repeat(100)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "\uD83D\uDE00".repeat(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("prefix")));
    }

    @Test
    void requiresAnEnabledHrUserAndInvalidatesTheirSessionWhenDisabled() throws Exception {
        mvc.perform(get(PATH).queryParam("prefix", "R")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get(PATH).with(user(EMAIL).roles("USER")).queryParam("prefix", "R"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        jdbc.update("UPDATE hr_user SET enabled=false WHERE work_email=?", EMAIL);
        var session = new MockHttpSession();
        mvc.perform(get(PATH).session(session).with(user(EMAIL).roles("HR")).queryParam("prefix", "R"))
                .andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }

    @Test
    void followsExistingBodyAcceptAndMethodRules() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R").accept("text/html"))
                .andExpect(status().isNotAcceptable());
        mvc.perform(head(PATH).with(user(EMAIL).roles("HR")).queryParam("prefix", "R"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"));
        mvc.perform(post(PATH).with(user(EMAIL).roles("HR")).with(csrf().asHeader()).queryParam("prefix", "R"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"));
    }

    private void name(int number, String name) {
        jdbc.update("UPDATE employee SET full_name=? WHERE employee_id=?", name, "EMP-%06d".formatted(number));
    }

    private void categories(int number, String country, String department, String level, String currency) {
        jdbc.update("UPDATE employee SET country_code=?, department=?, job_level=?, currency_code=? WHERE employee_id=?",
                country, department, level, currency, "EMP-%06d".formatted(number));
    }
}
