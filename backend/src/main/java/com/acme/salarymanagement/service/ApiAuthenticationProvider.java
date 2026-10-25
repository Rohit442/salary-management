package com.acme.salarymanagement.service;

import com.acme.salarymanagement.exception.NonHrAuthenticationException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

public class ApiAuthenticationProvider extends DaoAuthenticationProvider {
    public ApiAuthenticationProvider(UserDetailsService users, PasswordEncoder encoder) {
        super(users);
        setPasswordEncoder(encoder);
    }

    @Override
    protected Authentication createSuccessAuthentication(Object principal, Authentication authentication, UserDetails user) {
        boolean hr = user.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_HR"));
        if (!hr) throw new NonHrAuthenticationException();
        return super.createSuccessAuthentication(principal, authentication, user);
    }
}
