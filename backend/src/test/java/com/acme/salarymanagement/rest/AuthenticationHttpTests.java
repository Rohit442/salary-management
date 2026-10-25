package com.acme.salarymanagement.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.h2.console.enabled=false", "spring.datasource.url=jdbc:h2:mem:auth-http;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class AuthenticationHttpTests {
    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    private record InvalidForm(String body, int status, String code) { }
    private final String validForm = "email=maya.rao%40acme.example&password=AcmeDev%212026";

    @Test
    void completeHttpFlowEnforcesCookiesCsrfRotationAndLogout() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var issued = get(client, "/csrf");
        assertThat(issued.statusCode()).isEqualTo(200);
        String token = token(issued);
        String before = sessionId(cookies);
        String anonymousCookie = issued.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(anonymousCookie).contains("JSESSIONID=", "Path=/", "HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        var login = post(client, "/login", validForm, token);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Location")).isEmpty();
        String after = sessionId(cookies);
        assertThat(after).isNotEqualTo(before);
        var me = get(client, "/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body())).isEqualTo(json.readTree(login.body()));
        assertThat(me.body()).doesNotContain("password", "passwordHash", "JSESSIONID", "{bcrypt}");
        assertThat(post(client, "/logout", "", token).statusCode()).isEqualTo(403);
        assertThat(get(client, "/me").statusCode()).isEqualTo(200);
        token = token(get(client, "/csrf"));
        // Shape validation must run before Spring terminates the authenticated session.
        assertThat(post(client, "/logout", "unexpected=body", token).statusCode()).isEqualTo(415);
        assertThat(get(client, "/logout").statusCode()).isEqualTo(405);
        assertThat(get(client, "/me").statusCode()).isEqualTo(200);
        var logout = post(client, "/logout", "", token);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.body()).isEmpty();
        assertThat(logout.headers().firstValue("Content-Type")).isEmpty();
        assertThat(logout.headers().firstValue("Set-Cookie").orElseThrow())
                .contains("JSESSIONID=", "Max-Age=0", "HttpOnly", "SameSite=Lax");
        assertThat(get(client, "/me").statusCode()).isEqualTo(401);
        var stale = HttpClient.newHttpClient().send(request("/me").header("Cookie", "JSESSIONID=" + after)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(stale.statusCode()).isEqualTo(401);
        String fresh = token(get(client, "/csrf"));
        assertThat(post(client, "/logout", "", fresh).statusCode()).isEqualTo(204);
    }

    @ParameterizedTest
    @MethodSource("invalidForms")
    void rawFormValidationRejectsBadRequestsWithoutAuthenticating(InvalidForm form) throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String token = token(get(client, "/csrf"));
        var response = post(client, "/login", form.body(), token);
        assertThat(response.statusCode()).isEqualTo(form.status());
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo(form.code());
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(get(client, "/me").statusCode()).isEqualTo(401);
    }

    @Test
    void chunkedLoginCannotBypassTheEncodedBodyLimit() throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String token = token(get(client, "/csrf"));
        byte[] bytes = "x".repeat(4097).getBytes(StandardCharsets.UTF_8);
        var response = client.send(request("/login").header("X-CSRF-TOKEN", token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    private static Stream<InvalidForm> invalidForms() {
        return Stream.of(
                new InvalidForm("email=%ZZ&password=x", 400, "VALIDATION_ERROR"),
                new InvalidForm("email=%FF&password=x", 400, "VALIDATION_ERROR"),
                new InvalidForm("email=maya.rao%40acme.example&password=x&password=y", 400, "VALIDATION_ERROR"),
                new InvalidForm("email=maya.rao%40acme.example&password=" + "x".repeat(129), 400, "VALIDATION_ERROR"),
                new InvalidForm("email=x&password=" + "x".repeat(4079), 400, "VALIDATION_ERROR"),
                new InvalidForm("email=x&password=" + "x".repeat(4080), 413, "PAYLOAD_TOO_LARGE"));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth" + path)).header("Accept", "application/json");
    }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient client, String path, String body, String token) throws Exception {
        var builder = request(path).header("X-CSRF-TOKEN", token);
        if (!body.isEmpty()) builder.header("Content-Type", "application/x-www-form-urlencoded");
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("token").asText();
    }
    private String sessionId(CookieManager manager) {
        return manager.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .findFirst().orElseThrow().getValue();
    }
}
