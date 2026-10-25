package com.acme.salarymanagement.impl;

import com.acme.salarymanagement.bean.AuthResponse;
import com.acme.salarymanagement.bean.CsrfResponse;
import com.acme.salarymanagement.bean.UserResponse;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.service.AuthService;
import com.acme.salarymanagement.util.EmailUtility;
import com.acme.salarymanagement.util.SqlUtility;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    private record Account(String id, String email, String name, String role, boolean enabled) { }
    private final SqlUtility sql;

    public AuthServiceImpl(SqlUtility sql) { this.sql = sql; }

    @Override
    public CsrfResponse issueCsrfToken(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @Override
    public AuthResponse currentUser(String email, int idleTimeoutSeconds) {
        Account account;
        try {
            account = sql.queryOne("""
                    SELECT public_id, work_email, display_name, role, enabled
                    FROM hr_user WHERE work_email = ?
                    """, (row, index) -> new Account(row.getString("public_id"), row.getString("work_email"),
                    row.getString("display_name"), row.getString("role"), row.getBoolean("enabled")), EmailUtility.normalize(email));
        } catch (EmptyResultDataAccessException exception) {
            throw unauthenticated();
        } catch (DataAccessException exception) {
            throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Authentication is temporarily unavailable.", List.of(), exception);
        }
        if (!account.enabled()) throw unauthenticated();
        if (!"HR".equals(account.role())) {
            throw new ApiRequestException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "You do not have access to this HR workspace.", List.of());
        }
        return new AuthResponse(new UserResponse(account.id(), account.email(), account.name(), List.of("HR")), idleTimeoutSeconds);
    }

    private ApiRequestException unauthenticated() {
        return new ApiRequestException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Please sign in to access the HR workspace.", List.of());
    }
}
