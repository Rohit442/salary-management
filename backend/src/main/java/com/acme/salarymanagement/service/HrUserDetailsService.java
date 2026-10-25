package com.acme.salarymanagement.service;

import com.acme.salarymanagement.util.EmailUtility;
import com.acme.salarymanagement.util.SqlUtility;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class HrUserDetailsService implements UserDetailsService {
    private final SqlUtility sql;

    public HrUserDetailsService(SqlUtility sql) {
        this.sql = sql;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalizedEmail = EmailUtility.normalize(email);
        try {
            return sql.queryOne("""
                    SELECT work_email, password_hash, role, enabled
                    FROM hr_user WHERE work_email = ?
                    """, (row, index) -> User.withUsername(row.getString("work_email"))
                    .password(row.getString("password_hash"))
                    .roles(row.getString("role"))
                    .disabled(!row.getBoolean("enabled"))
                    .build(), normalizedEmail);
        } catch (EmptyResultDataAccessException exception) {
            throw new UsernameNotFoundException("Invalid email or password.");
        }
    }
}
