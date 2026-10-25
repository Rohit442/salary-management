package com.acme.salarymanagement;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class DemoAccountContextTests {
    @TempDir Path directory;

    @Test
    void freshDatabaseOutsideDemoContextHasNoDemoLogin() {
        try (var application = new SpringApplicationBuilder(SalaryManagementApplication.class)
                .profiles("test").web(WebApplicationType.NONE).run(
                        "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("no-demo"),
                        "--spring.liquibase.contexts=production")) {
            var jdbc = application.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_user WHERE work_email = ?", Integer.class,
                    "maya.rao@acme.example")).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_user WHERE enabled = TRUE", Integer.class)).isZero();
        }
    }
}
