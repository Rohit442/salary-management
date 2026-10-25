package com.acme.salarymanagement.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.h2.console.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:csrf-api;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfApiTests {
    private static final String PATH = "/api/auth/csrf";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void anonymousTokenMatchesTheSessionRepositoryWithoutAuthenticatingTheSession() throws Exception {
        var result = mvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        var stored = new HttpSessionCsrfTokenRepository().loadToken(result.getRequest());
        assertThat(stored).isNotNull();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("token").asText()).isEqualTo(stored.getToken());
        assertThat(body.get("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
                .andExpect(resultAgain -> assertThat(resultAgain.getRequest().getSession(false).getId()).isEqualTo(session.getId()));
    }

    @Test
    void ignoresClientSuppliedRequestIdsAndDoesNotCacheTokens() throws Exception {
        var result = mvc.perform(get(PATH).header("X-Request-ID", "client-controlled"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn();
        String requestId = result.getResponse().getHeader("X-Request-ID");
        assertThat(requestId).isNotEqualTo("client-controlled");
        UUID.fromString(requestId);
    }

    @Test
    void unauthenticatedApiRequestsDoNotCreateASavedRequestSession() throws Exception {
        var result = error(mvc.perform(get("/api/auth/me")), 401, "AUTHENTICATION_REQUIRED").andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void rejectsQueryParameters() throws Exception {
        error(mvc.perform(get(PATH).queryParam("debug", "true")), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("query")));
    }

    @Test
    void rejectsARequestBody() throws Exception {
        error(mvc.perform(get(PATH).contentType(MediaType.APPLICATION_JSON).content("{}")), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void rejectsClientsThatExcludeJson() throws Exception {
        error(mvc.perform(get(PATH).accept(MediaType.TEXT_HTML)), 406, "NOT_ACCEPTABLE");
    }

    @Test
    void unsupportedMethodsWithValidCsrfReturnAllowAndJson() throws Exception {
        error(mvc.perform(put(PATH).with(user("hr").roles("HR")).with(csrf().asHeader())), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void csrfRejectionOccursBeforeMethodValidation() throws Exception {
        error(mvc.perform(post(PATH).with(user("hr").roles("HR"))), 403, "CSRF_INVALID");
    }

    @Test
    void apiCsrfTokensMustComeFromTheHeaderRatherThanAFormParameter() throws Exception {
        var issued = mvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) issued.getRequest().getSession(false);
        String token = json.readTree(issued.getResponse().getContentAsString()).get("token").asText();
        error(mvc.perform(post(PATH).session(session).with(user("hr").roles("HR"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("_csrf", token)), 403, "CSRF_INVALID");
        error(mvc.perform(put(PATH).session(session).with(user("hr").roles("HR"))
                .header("X-CSRF-TOKEN", token)), 405, "METHOD_NOT_ALLOWED");
    }

    private ResultActions error(ResultActions result, int statusCode, String code) throws Exception {
        return result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andDo(response -> {
                    String id = response.getResponse().getHeader("X-Request-ID");
                    UUID.fromString(id);
                    assertThat(json.readTree(response.getResponse().getContentAsString()).get("requestId").asText()).isEqualTo(id);
                });
    }
}
