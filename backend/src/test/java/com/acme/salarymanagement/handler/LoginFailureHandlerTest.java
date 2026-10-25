package com.acme.salarymanagement.handler;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.WebAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class LoginFailureHandlerTest {
    private final LoginFailureHandler handler = new LoginFailureHandler();

    @ParameterizedTest
    @MethodSource("failures")
    void browserFailuresExposeOneGenericMessageWithoutInternalDetails(AuthenticationException cause) throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, cause);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
        var exposed = (AuthenticationException) request.getSession(false)
                .getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        assertThat(exposed).isInstanceOf(BadCredentialsException.class);
        assertThat(exposed.getMessage()).isEqualTo("Invalid email or password.");
        assertThat(exposed.getCause()).isNull();
        assertThat(response.getContentAsString()).doesNotContain(cause.getMessage());
    }

    private static Stream<AuthenticationException> failures() {
        return Stream.of(new BadCredentialsException("Internal password mismatch"),
                new UsernameNotFoundException("Internal account not found"),
                new DisabledException("Internal disabled account"),
                new LockedException("Internal account lock"),
                new AuthenticationServiceException("Internal authentication dependency failure"));
    }
}
