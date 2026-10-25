package com.acme.salarymanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.WebAttributes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HrLoginTests {
    @Autowired private MockMvc mvc;

    @Test
    void enabledDatabaseUserCanSignInWithNormalizedEmail() throws Exception {
        mvc.perform(formLogin("/login").user("email", "  MAYA.RAO@ACME.EXAMPLE  ").password("AcmeDev!2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername("maya.rao@acme.example"));
    }

    @Test
    void unknownDisabledAndWrongPasswordFailuresAreGeneric() throws Exception {
        for (String email : new String[]{"unknown@acme.example", "seed@acme.example", "maya.rao@acme.example"}) {
            var result = mvc.perform(formLogin("/login").user("email", email).password("wrong-password"))
                    .andExpect(unauthenticated())
                    .andExpect(redirectedUrl("/login?error"))
                    .andReturn();
            var failure = (Exception) result.getRequest().getSession(false)
                    .getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            assertThat(failure.getMessage()).isEqualTo("Invalid email or password.");
        }
    }
}
