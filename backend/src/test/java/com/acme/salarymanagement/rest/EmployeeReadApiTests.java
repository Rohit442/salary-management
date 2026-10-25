package com.acme.salarymanagement.rest;

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

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.h2.console.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:employee-read-api;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeReadApiTests {
    private static final String PATH = "/api/employees";
    private static final String EMAIL = "maya.rao@acme.example";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        jdbc.update("UPDATE employee SET full_name='Same name'");
        jdbc.update("UPDATE employee SET full_name='100%_Example' WHERE employee_id='EMP-000001'");
    }

    @Test
    void defaultsIncludeInactiveAndReturnTheCompletePublicShape() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.totalElements").value(20)).andExpect(jsonPath("$.numberOfElements").value(20))
                .andExpect(jsonPath("$.size").value(25)).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[*].employmentStatus", hasItem("INACTIVE")))
                .andExpect(jsonPath("$.items[0].employeeId").value("EMP-000001"))
                .andExpect(jsonPath("$.items[0].annualBaseSalary", matchesPattern("[0-9]+\\.[0-9]{2}")))
                .andExpect(jsonPath("$.items[0].joiningDate").isString())
                .andExpect(jsonPath("$.items[0].initialSalaryEffectiveFrom").isString())
                .andExpect(jsonPath("$.items[0].salaryEffectiveFrom").isString())
                .andExpect(jsonPath("$.items[0].createdAt", endsWith("Z")))
                .andExpect(jsonPath("$.items[0].version").value(0))
                .andExpect(jsonPath("$.items[0].id").doesNotExist());
    }

    @Test
    void paginationUsesStableInternalIdTieBreakerAndCorrectTotals() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("page", "1").queryParam("size", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].employeeId", contains("EMP-000004", "EMP-000005", "EMP-000006")))
                .andExpect(jsonPath("$.totalElements").value(20)).andExpect(jsonPath("$.totalPages").value(7))
                .andExpect(jsonPath("$.hasPrevious").value(true)).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("page", "1000000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(20)).andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void filtersCombineOrAndAndSearchMatchesIdsWithoutCaseSensitivity() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR"))
                        .queryParam("country", "IN", "US", "IN").queryParam("department", "Engineering")
                        .queryParam("currency", "INR").queryParam("jobLevel", "L1").queryParam("q", " emp-000001 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchTreatsWildcardsAndSqlInjectionAsLiteralText() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("q", "%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("q", "' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void supportedDescendingSortUsesWhitelistedColumn() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("sort", "employeeId,desc").queryParam("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].employeeId").value("EMP-000020"))
                .andExpect(jsonPath("$.sort.field").value("employeeId")).andExpect(jsonPath("$.sort.direction").value("desc"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=1000001", "page=99999999999999999999", "page=1.0", "size=0", "size=101",
            "sort=annualBaseSalary,asc", "sort=fullName,DESC", "country=in", "country=IN,US", "q=", "groupBy=country"})
    void rejectsInvalidQueryParameters(String query) throws Exception {
        var pair = query.split("=", -1);
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam(pair[0], pair[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsRepeatedScalarsTooManyFiltersAndLongSearch() throws Exception {
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("page", "0", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR")).queryParam("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user(EMAIL).roles("HR"))
                        .queryParam("country", java.util.Collections.nCopies(21,"IN").toArray(String[]::new)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailsReturnInactiveEmployeeAndDistinctNotFoundAndInvalidIdErrors() throws Exception {
        mvc.perform(get(PATH + "/EMP-000011").with(user(EMAIL).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.employmentStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.lastEmploymentDate").isString());
        mvc.perform(get(PATH + "/EMP-999999").with(user(EMAIL).roles("HR")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));
        mvc.perform(get(PATH + "/invalid").with(user(EMAIL).roles("HR")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void accessAndProtocolRulesMatchOtherApis() throws Exception {
        for (String path : List.of(PATH, PATH + "/EMP-000001")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            mvc.perform(get(path).with(user(EMAIL).roles("USER"))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user(EMAIL).roles("HR")).content("{}"))
                    .andExpect(status().isUnsupportedMediaType());
            mvc.perform(get(path).with(user(EMAIL).roles("HR")).accept("text/html"))
                    .andExpect(status().isNotAcceptable());
            mvc.perform(head(path).with(user(EMAIL).roles("HR")))
                    .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")));
            jdbc.update("UPDATE hr_user SET enabled=false WHERE work_email=?", EMAIL);
            mvc.perform(get(path).with(user(EMAIL).roles("HR"))).andExpect(status().isUnauthorized());
            jdbc.update("UPDATE hr_user SET enabled=true WHERE work_email=?", EMAIL);
        }
        mvc.perform(get(PATH + "/EMP-000001").with(user(EMAIL).roles("HR")).queryParam("q", "x"))
                .andExpect(status().isBadRequest());
    }
}
