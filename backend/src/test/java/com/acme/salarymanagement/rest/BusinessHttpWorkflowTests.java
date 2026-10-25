package com.acme.salarymanagement.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:business-http;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class BusinessHttpWorkflowTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    String token;
    @Test void authenticatedEmployeeSalaryEstimateAndLogoutWorkflow() throws Exception {
        token=json.readTree(send("GET","/api/auth/csrf",null,null).body()).get("token").asText();
        var login=client.send(builder("/api/auth/login").header("Content-Type","application/x-www-form-urlencoded").header("X-CSRF-TOKEN",token)
                .POST(HttpRequest.BodyPublishers.ofString("email=maya.rao%40acme.example&password=AcmeDev%212026")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        token=json.readTree(send("GET","/api/auth/csrf",null,null).body()).get("token").asText();
        assertThat(send("GET","/api/reference-data",null,null).statusCode()).isEqualTo(200);
        assertThat(send("GET","/api/employees?page=0&size=1",null,null).statusCode()).isEqualTo(200);
        String employee="/api/employees/EMP-010001";
        var created=send("POST","/api/employees","""
                {"employeeId":"EMP-010001","fullName":"HTTP Employee","workEmail":"http@acme.example","countryCode":"IN",
                "department":"Engineering","jobTitle":"Engineer","jobLevel":"L3","annualBaseSalary":"1200000.00","currency":"INR",
                "joiningDate":"2026-04-01","initialSalaryEffectiveFrom":"2026-04-01"}
                """,null);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(send("GET",employee,null,null).statusCode()).isEqualTo(200);
        var edited=send("PUT",employee+"/details","""
                {"fullName":"HTTP Employee","workEmail":"http@acme.example","countryCode":"IN","department":"Product",
                "jobTitle":"Manager","jobLevel":"L4","joiningDate":"2026-04-01","lastEmploymentDate":"2026-11-12","version":0}
                """,null);
        assertThat(edited.statusCode()).isEqualTo(200);
        String body="{\"annualBaseSalary\":\"1800000.00\",\"effectiveDate\":\"2026-10-15\",\"reason\":\"Review\",\"version\":1}";
        String key=UUID.randomUUID().toString();
        var salary=send("POST",employee+"/salary-changes",body,key);
        assertThat(salary.statusCode()).isEqualTo(201);
        var replay=send("POST",employee+"/salary-changes",body,key);
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("Idempotency-Replayed")).contains("true");
        assertThat(replay.body()).isEqualTo(salary.body());
        assertThat(send("GET",employee+"/salary-changes",null,null).statusCode()).isEqualTo(200);
        var estimate=send("GET",employee+"/base-pay-estimate?month=2026-11",null,null);
        assertThat(estimate.statusCode()).isEqualTo(200);
        assertThat(json.readTree(estimate.body()).get("estimatedBasePay").asText()).isEqualTo("60000.00");
        assertThat(send("GET","/api/reports/compensation?country=IN&groupBy=department",null,null).statusCode()).isEqualTo(200);
        assertThat(send("PUT",employee+"/status","{\"employmentStatus\":\"INACTIVE\",\"version\":2}",null).statusCode()).isEqualTo(200);
        assertThat(send("POST","/api/auth/logout",null,null).statusCode()).isEqualTo(204);
        assertThat(send("GET",employee,null,null).statusCode()).isEqualTo(401);
    }
    HttpRequest.Builder builder(String path) { return HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Accept","application/json"); }
    HttpResponse<String> send(String method,String path,String body,String key) throws Exception {
        var request=builder(path);
        if(!method.equals("GET")) request.header("X-CSRF-TOKEN",token);
        if(body!=null) request.header("Content-Type","application/json");
        if(key!=null) request.header("Idempotency-Key",key);
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
