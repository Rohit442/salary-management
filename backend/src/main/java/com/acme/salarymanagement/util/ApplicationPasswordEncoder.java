package com.acme.salarymanagement.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/** Preserves legacy BCrypt hashes; new hashes support the full password contract. */
public class ApplicationPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate = new DelegatingPasswordEncoder("pbkdf2@SpringSecurity_v5_8",
            Map.of("pbkdf2@SpringSecurity_v5_8", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                    "bcrypt", new BCryptPasswordEncoder()));

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) return false;
        if (encodedPassword.startsWith("{bcrypt}")
                && rawPassword.toString().getBytes(StandardCharsets.UTF_8).length > 72) return false;
        return delegate.matches(rawPassword, encodedPassword);
    }
}
