package com.acme.salarymanagement.service;

import com.acme.salarymanagement.util.SqlUtility;
import java.sql.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HrUserDetailsServiceTest {
    @Mock private SqlUtility sql;
    @Mock private ResultSet row;
    private HrUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new HrUserDetailsService(sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {"maya.rao@acme.example", "  MAYA.RAO@ACME.EXAMPLE  ", "\tMaya.Rao@acme.example\n"})
    void normalizesOnlyTheLookupEmailAndPreservesStoredAuthenticationData(String email) throws Exception {
        stubAccount(true);

        UserDetails user = service.loadUserByUsername(email);

        assertThat(user.getUsername()).isEqualTo("maya.rao@acme.example");
        assertThat(user.getPassword()).isEqualTo("{bcrypt}opaque-stored-hash");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_HR");
        verify(sql).queryOne(anyString(), any(), eq("maya.rao@acme.example"));
    }

    @Test
    void preservesDisabledStatusForSpringToRejectAtAuthentication() throws Exception {
        stubAccount(false);
        assertThat(service.loadUserByUsername("maya.rao@acme.example").isEnabled()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "unknown@acme.example"})
    void mapsMissingAccountsToAGenericCredentialFailure(String email) {
        when(sql.queryOne(anyString(), any(), anyString()))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> service.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void doesNotMisreportADatabaseOutageAsAnUnknownAccount() {
        var outage = new DataAccessResourceFailureException("Database unavailable");
        when(sql.queryOne(anyString(), any(), anyString())).thenThrow(outage);

        assertThatThrownBy(() -> service.loadUserByUsername("maya.rao@acme.example")).isSameAs(outage);
    }

    private void stubAccount(boolean enabled) throws Exception {
        when(row.getString("work_email")).thenReturn("maya.rao@acme.example");
        when(row.getString("password_hash")).thenReturn("{bcrypt}opaque-stored-hash");
        when(row.getString("role")).thenReturn("HR");
        when(row.getBoolean("enabled")).thenReturn(enabled);
        when(sql.<UserDetails>queryOne(anyString(), any(), anyString())).thenAnswer(invocation -> {
            RowMapper<UserDetails> mapper = invocation.getArgument(1);
            return mapper.mapRow(row, 0);
        });
    }
}
