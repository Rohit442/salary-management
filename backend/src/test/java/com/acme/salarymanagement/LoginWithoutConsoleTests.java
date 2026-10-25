package com.acme.salarymanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

@SpringBootTest(properties = {"spring.h2.console.enabled=false", "spring.datasource.url=jdbc:h2:mem:login-without-console"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginWithoutConsoleTests {
    @Autowired private MockMvc mvc;

    @Test
    void databaseLoginWorksWithTheH2ConsoleDisabled() throws Exception {
        mvc.perform(formLogin("/login").user("email", "maya.rao@acme.example").password("AcmeDev!2026"))
                .andExpect(authenticated().withUsername("maya.rao@acme.example"));
    }
}
