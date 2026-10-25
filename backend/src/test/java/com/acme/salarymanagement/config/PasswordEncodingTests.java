package com.acme.salarymanagement.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncodingTests {
    private final PasswordEncoder encoder = new SecurityConfiguration().passwordEncoder();

    @ParameterizedTest
    @ValueSource(strings = {"a", "密"})
    void supportsAll128CharactersWithoutIgnoringTheSuffix(String character) {
        String password = character.repeat(128);
        String hash = encoder.encode(password);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches(character.repeat(127) + "b", hash)).isFalse();
    }

    @Test
    void preservesExistingBcryptHashesAndRejectsOverlongCandidates() {
        String hash = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("legacy-password");
        assertThat(encoder.matches("legacy-password", hash)).isTrue();
        assertThat(encoder.matches("x".repeat(128), hash)).isFalse();
    }
}
