package com.acme.salarymanagement;

import java.net.URI;
import java.net.URLEncoder;
import java.util.regex.Pattern;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.h2.console.enabled=true", "spring.security.user.password=console-test-password"})
@ActiveProfiles("test")
class H2ConsoleSecurityTests {
    @LocalServerPort private int port;
    @Value("${spring.datasource.url}") private String jdbcUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void authenticatedConsoleAllowsFramesAndItsFormPost() throws Exception {
        var page = client.send(request("/h2-console/", true).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");
        var session = Pattern.compile("jsessionid=([a-zA-Z0-9]+)").matcher(page.body());
        assertThat(session.find()).isTrue();
        String sessionId = session.group(1);
        String credentials = "driver=org.h2.Driver&url=" + URLEncoder.encode(jdbcUrl, StandardCharsets.UTF_8)
                + "&user=sa&password=";
        var login = client.send(request("/h2-console/login.do?jsessionid=" + sessionId, true)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(credentials)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        var query = client.send(request("/h2-console/query.do?jsessionid=" + sessionId, true)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("sql=" + URLEncoder.encode(
                        "SELECT COUNT(*) AS employee_count FROM employee", StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(query.statusCode()).isEqualTo(200);
        assertThat(query.body()).contains("EMPLOYEE_COUNT").contains("20");
    }

    @Test
    void consoleRequiresSignIn() throws Exception {
        var response = client.send(request("/h2-console/", false).header("Accept", "text/html")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/login");
    }

    @Test
    void apiStillRequiresAuthenticationAndCsrf() throws Exception {
        var anonymous = client.send(request("/api/employees", false).header("Accept", "application/json")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);
        var authenticated = client.send(request("/api/employees", true).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(authenticated.statusCode()).isEqualTo(404);
        assertThat(authenticated.headers().firstValue("X-Frame-Options")).contains("DENY");
        var unsafe = client.send(request("/api/employees", true).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        // A protected /error redispatch can return 401 after the CSRF rejection.
        assertThat(unsafe.statusCode()).isIn(401, 403);
    }

    private HttpRequest.Builder request(String path, boolean authenticated) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (authenticated) {
            String credentials = Base64.getEncoder().encodeToString(
                    "user:console-test-password".getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + credentials);
        }
        return builder;
    }
}
