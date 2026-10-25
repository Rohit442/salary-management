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

    @ParameterizedTest
    @ValueSource(strings = {"prefix=", "prefix=   ", "limit=0", "limit=11", "limit=-1", "limit=1.5",
            "limit=9999999999999999999999", "limit=", "q=R", "country=IN", "page=0", "sort=fullName,asc"})
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
}
