package com.acme.salarymanagement.config;

import com.acme.salarymanagement.handler.LoginFailureHandler;
import com.acme.salarymanagement.handler.ApiAuthenticationHandlers;
import com.acme.salarymanagement.filter.AuthValidationFilter;
import com.acme.salarymanagement.service.AuthService;
import com.acme.salarymanagement.service.ApiAuthenticationProvider;
import com.acme.salarymanagement.service.HrUserDetailsService;
import com.acme.salarymanagement.service.LoginAttemptLimiter;
import com.acme.salarymanagement.util.SessionUtility;
import com.acme.salarymanagement.util.ApplicationPasswordEncoder;
import com.acme.salarymanagement.validator.AuthRequestValidator;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ProviderManager;
import com.acme.salarymanagement.handler.ApiCsrfTokenRequestHandler;
import com.acme.salarymanagement.filter.ApiRequestFilter;
import com.acme.salarymanagement.util.ApiResponseWriter;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new ApplicationPasswordEncoder();
    }

    @Bean
    Clock authClock() { return Clock.systemUTC(); }

    @Bean
    LoginAttemptLimiter loginAttemptLimiter(Clock authClock,
            @Value("${app.auth.maximum-failures:5}") int maximum,
            @Value("${app.auth.attempt-window-seconds:900}") long windowSeconds) {
        return new LoginAttemptLimiter(authClock, maximum, Duration.ofSeconds(windowSeconds));
    }

    @Bean
    @Order(0)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain authApiSecurityFilterChain(HttpSecurity http, ApiResponseWriter responses,
            AuthRequestValidator validator, LoginAttemptLimiter attempts, AuthService service,
            SessionUtility sessions, HrUserDetailsService users, PasswordEncoder encoder) throws Exception {
        var handlers = new ApiAuthenticationHandlers(service, attempts, responses, sessions);
        var provider = new ApiAuthenticationProvider(users, encoder);
        return http
                .securityMatcher("/api/auth/**", "/api/reports/**", "/api/reference-data")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .anyRequest().hasRole("HR"))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .csrfTokenRequestHandler(new ApiCsrfTokenRequestHandler()))
                .requestCache(cache -> cache.disable())
                .authenticationManager(new ProviderManager(provider))
                .formLogin(form -> form.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                        .successHandler(handlers::success).failureHandler(handlers::failure))
                .logout(logout -> logout.logoutRequestMatcher(request -> request.getMethod().equals("POST")
                                && request.getRequestURI().equals(request.getContextPath() + "/api/auth/logout"))
                        .addLogoutHandler((request, response, authentication) -> sessions.expireCookie(request, response))
                        .logoutSuccessHandler(handlers::logout))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> responses.error(request, response,
                                401, "AUTHENTICATION_REQUIRED", "Please sign in to access the HR workspace.", List.of()))
                        .accessDeniedHandler((request, response, exception) -> {
                            boolean csrfFailure = exception instanceof CsrfException;
                            responses.error(request, response, 403,
                                    csrfFailure ? "CSRF_INVALID" : "ACCESS_DENIED",
                                    csrfFailure ? "Your security token is no longer valid. Refresh it and try again."
                                            : "You do not have access to this HR workspace.", List.of());
                        }))
                .addFilterBefore(new ApiRequestFilter(responses), CsrfFilter.class)
                .addFilterAfter(new AuthValidationFilter(validator, attempts, responses), CsrfFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http, LoginFailureHandler failureHandler) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("HR"))
                .formLogin(form -> form.usernameParameter("email").failureHandler(failureHandler))
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
