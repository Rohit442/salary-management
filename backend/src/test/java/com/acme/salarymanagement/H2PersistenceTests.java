package com.acme.salarymanagement;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class H2PersistenceTests {
    @TempDir Path directory;

    @Test
    void upgradesExistingDatasetAndRetainsItAcrossRestart() {
        String url = "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("restart-test");
        String count = "--spring.liquibase.parameters.employeeCount=10000";
        try (var legacy = application().run(url, count,
                "--spring.liquibase.change-log=classpath:db/changelog/legacy-bootstrap.yaml")) {
            var jdbc = legacy.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee", Integer.class)).isEqualTo(10000);
            jdbc.update("UPDATE employee SET full_name = ? WHERE employee_id = ?", "Edited name", "EMP-000001");
        }
        for (int restart = 0; restart < 2; restart++) {
            try (var current = application().run(url, count)) {
                var jdbc = current.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee", Integer.class)).isEqualTo(10000);
                assertThat(jdbc.queryForObject("SELECT full_name FROM employee WHERE employee_id = ?", String.class, "EMP-000001"))
                        .isEqualTo("Edited name");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee e JOIN hr_user h ON h.id = e.created_by_fk JOIN currency c ON c.code = e.currency_code WHERE h.enabled = FALSE", Integer.class))
                        .isEqualTo(10000);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE id = '004-seed-demo-employees'", Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC' AND table_name = 'SAMPLE_MESSAGE'", Integer.class)).isZero();
            }
        }
    }

    private SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(SalaryManagementApplication.class)
                .profiles("test").web(WebApplicationType.NONE);
    }
}
