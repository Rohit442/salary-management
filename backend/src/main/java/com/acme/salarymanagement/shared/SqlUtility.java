package com.acme.salarymanagement.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** Spring JDBC manages connections and resources, including transaction participation. */
@Component
public class SqlUtility {
    private final JdbcTemplate jdbc;

    public SqlUtility(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public <T> T queryOne(String sql, RowMapper<T> mapper, Object... parameters) {
        return jdbc.queryForObject(sql, mapper, parameters);
    }
}
