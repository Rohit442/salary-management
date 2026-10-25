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
    void restartPreservesDataAndDoesNotRepeatLiquibaseSeed() {
        String url = "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("restart-test");
        try (var first = application().run(url)) {
            var jdbc = first.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT message FROM sample_message WHERE id = 1", String.class))
                    .isEqualTo("hello Rohit");
            jdbc.update("UPDATE sample_message SET message = ? WHERE id = ?", "saved across restart", 1L);
        }
        try (var second = application().run(url)) {
            var jdbc = second.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample_message", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT message FROM sample_message WHERE id = 1", String.class))
                    .isEqualTo("saved across restart");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class)).isEqualTo(2);
        }
    }

    private SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(SalaryManagementApplication.class)
                .profiles("test").web(WebApplicationType.NONE);
    }
}
