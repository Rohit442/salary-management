package com.acme.salarymanagement.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contract expectations from docs/api-auth.md; pending API behavior remains in the red phase. */
@SpringBootTest(properties = {"spring.h2.console.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:auth-api-contract;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("auth-api-contract")
class AuthenticationApiContractTest {
    private static final String EMAIL = "maya.rao@acme.example";
    private static final String PASSWORD = "AcmeDev!2026";
    private static final String ROOT = "/api/auth";
    private static final AtomicInteger CLIENT_NUMBER = new AtomicInteger();
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    private String clientIp;

    @BeforeEach
    void isolateAttemptCountersByClient() {
        clientIp = "192.0.2." + CLIENT_NUMBER.incrementAndGet();
    }

    @Test
    void csrfIsPublicAndCreatesAnAnonymousSessionWithAnOpaqueToken() throws Exception {
        var result = perform(get(ROOT + "/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertCommonHeaders(result.getResponse());
    }

    @Test
    void loginReturnsTheHrUserAsJsonWithoutARedirect() throws Exception {
        assertUser(perform(login("  MAYA.RAO@ACME.EXAMPLE  ", PASSWORD)))
                .andExpect(header().doesNotExist("Location"));
    }

    @ParameterizedTest
    @CsvSource({"unknown@acme.example,wrong", "seed@acme.example,wrong", "maya.rao@acme.example,wrong"})
    void credentialFailuresHaveTheSameStatusAndMessage(String email, String password) throws Exception {
        error(login(email, password), 401, "INVALID_CREDENTIALS")
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void loginDoesNotTrimThePassword() throws Exception {
        error(login(EMAIL, " " + PASSWORD + " "), 401, "INVALID_CREDENTIALS");
    }

    @ParameterizedTest
    @CsvSource({"email,''", "email,invalid-email", "password,''"})
    void invalidRequiredFieldsProduceValidationErrors(String field, String value) throws Exception {
        var request = login(field.equals("email") ? value : EMAIL, field.equals("password") ? value : PASSWORD);
        error(request, 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(field)));
    }

    @Test
    void missingEmailIsRejected() throws Exception {
        error(post(ROOT + "/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("password", PASSWORD).with(csrf().asHeader()), 400, "VALIDATION_ERROR");
    }

    @Test
    void missingPasswordIsRejected() throws Exception {
        error(post(ROOT + "/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", EMAIL).with(csrf().asHeader()), 400, "VALIDATION_ERROR");
    }

    @Test
    void duplicateCredentialsAreRejected() throws Exception {
        error(login(EMAIL, PASSWORD).param("email", "other@acme.example"), 400, "VALIDATION_ERROR");
    }

    @Test
    void unknownFormFieldsAreRejected() throws Exception {
        error(login(EMAIL, PASSWORD).param("role", "HR"), 400, "VALIDATION_ERROR");
    }

    @Test
    void oversizedPasswordsAreRejectedBeforeAuthentication() throws Exception {
        error(login(EMAIL, "x".repeat(129)), 400, "VALIDATION_ERROR");
    }

    @Test
    void jsonLoginIsRejected() throws Exception {
        error(post(ROOT + "/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"maya.rao@acme.example\",\"password\":\"ignored\"}")
                .with(csrf().asHeader()), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void loginRequiresCsrfBeforeCheckingCredentials() throws Exception {
        error(post(ROOT + "/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", EMAIL).param("password", PASSWORD), 403, "CSRF_INVALID");
    }

    @Test
    void sixthCredentialFailureIsRateLimitedForTheSameEmailAndClient() throws Exception {
        for (int failure = 0; failure < 5; failure++) {
            error(login(failure % 2 == 0 ? EMAIL : " MAYA.RAO@ACME.EXAMPLE ", "wrong"), 401, "INVALID_CREDENTIALS");
        }
        var result = error(login(EMAIL, "wrong"), 429, "TOO_MANY_ATTEMPTS")
                .andExpect(header().exists("Retry-After")).andReturn();
        assertThat(Integer.parseInt(result.getResponse().getHeader("Retry-After"))).isPositive();
    }

    @Test
    void successfulLoginClearsTheCredentialFailureCounter() throws Exception {
        for (int failure = 0; failure < 4; failure++) {
            error(login(EMAIL, "wrong"), 401, "INVALID_CREDENTIALS");
        }
        assertUser(perform(login(EMAIL, PASSWORD)));
        for (int failure = 0; failure < 5; failure++) {
            error(login(EMAIL, "wrong"), 401, "INVALID_CREDENTIALS");
        }
    }

    @Test
    void meReturnsTheCurrentUserFromAnAuthenticatedSession() throws Exception {
        assertUser(perform(get(ROOT + "/me").session(browserSession())));
    }

    @Test
    void meRejectsAnonymousAccessWithJson() throws Exception {
        error(get(ROOT + "/me"), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    @Transactional
    void meRechecksDisabledStatusAndInvalidatesAnExistingSession() throws Exception {
        var session = browserSession();
        jdbc.update("UPDATE hr_user SET enabled = FALSE WHERE work_email = ?", EMAIL);
        error(get(ROOT + "/me").session(session), 401, "AUTHENTICATION_REQUIRED");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logoutInvalidatesTheSessionAndExpiresItsCookieWithoutAResponseBody() throws Exception {
        var session = browserSession();
        perform(post(ROOT + "/logout").session(session).with(csrf().asHeader()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist("Content-Type"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().path("JSESSIONID", "/"));
        assertThat(session.isInvalid()).isTrue();
        error(get(ROOT + "/me"), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void anonymousLogoutWithValidCsrfAlsoSucceeds() throws Exception {
        var result = perform(post(ROOT + "/logout").with(csrf().asHeader()))
                .andExpect(status().isNoContent()).andExpect(content().string("")).andReturn();
        assertCommonHeaders(result.getResponse());
    }

    @Test
    void logoutRejectsInvalidCsrfWithoutEndingTheSession() throws Exception {
        var session = browserSession();
        error(post(ROOT + "/logout").session(session).with(csrf().asHeader().useInvalidToken()), 403, "CSRF_INVALID");
        assertThat(session.isInvalid()).isFalse();
        assertUser(perform(get(ROOT + "/me").session(session)));
    }

    @Test
    void actualCsrfFlowRotatesTheSessionAndRejectsThePreLoginToken() throws Exception {
        var before = perform(get(ROOT + "/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) before.getRequest().getSession(false);
        String oldId = session.getId();
        String token = json.readTree(before.getResponse().getContentAsString()).get("token").asText();
        var signedIn = assertUser(perform(post(ROOT + "/login").session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", EMAIL).param("password", PASSWORD)
                .header("X-CSRF-TOKEN", token))).andReturn();
        var authenticated = (MockHttpSession) signedIn.getRequest().getSession(false);
        assertThat(authenticated.getId()).isNotEqualTo(oldId);
        error(post(ROOT + "/logout").session(authenticated).header("X-CSRF-TOKEN", token), 403, "CSRF_INVALID");
        var refreshed = perform(get(ROOT + "/csrf").session(authenticated)).andExpect(status().isOk()).andReturn();
        String newToken = json.readTree(refreshed.getResponse().getContentAsString()).get("token").asText();
        perform(post(ROOT + "/logout").session(authenticated).header("X-CSRF-TOKEN", newToken))
                .andExpect(status().isNoContent());
        assertThat(authenticated.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"/csrf,GET", "/login,POST", "/me,GET", "/logout,POST"})
    void unsupportedMethodsReturnJsonAndAnAllowHeader(String endpoint, String allowed) throws Exception {
        error(put(ROOT + endpoint).session(browserSession()).with(csrf().asHeader()), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().string("Allow", containsString(allowed)));
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post(ROOT + "/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", password).with(csrf().asHeader());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.accept(MediaType.APPLICATION_JSON).with(servlet -> {
            servlet.setRemoteAddr(clientIp);
            return servlet;
        }));
    }

    private ResultActions assertUser(ResultActions result) throws Exception {
        return result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user.id", not(emptyOrNullString())))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.displayName").value("Maya Rao"))
                .andExpect(jsonPath("$.user.roles", contains("HR")))
                .andExpect(jsonPath("$.idleTimeoutSeconds").value(1800))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andDo(response -> assertCommonHeaders(response.getResponse()));
    }

    private ResultActions error(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        return perform(request).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andDo(result -> {
                    assertCommonHeaders(result.getResponse());
                    assertThat(json.readTree(result.getResponse().getContentAsString()).get("requestId").asText())
                            .isEqualTo(result.getResponse().getHeader("X-Request-ID"));
                });
    }

    private void assertCommonHeaders(org.springframework.mock.web.MockHttpServletResponse response) {
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        String requestId = response.getHeader("X-Request-ID");
        assertThat(requestId).isNotBlank();
        UUID.fromString(requestId);
    }

    private MockHttpSession browserSession() throws Exception {
        var result = mvc.perform(formLogin("/login").user("email", EMAIL).password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(EMAIL)).andReturn();
        assertThat(result.getRequest().getSession(false)).isNotNull();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
