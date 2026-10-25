package com.acme.salarymanagement;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationFormatCompatibilityTests {
    // Captured from the original SQL migrations and Java reference-data custom change.
    private static final Map<String, String> LEGACY_CHECKSUMS = Map.ofEntries(
            Map.entry("005-create-hr-user", "9:5e3a971299c9cf8c1527224a90155f0a"),
            Map.entry("005-create-currency", "9:8c87c42dc6411c56cc97d864d92996f8"),
            Map.entry("006-seed-er-reference-data", "9:88aa3c523394f2e376ca75102806202e"),
            Map.entry("007-add-employee-creator", "9:5e4f4a04f470312ecbaf7255868f0432"),
            Map.entry("007-backfill-employee-creator", "9:1bfbbf1986683e54f20d7f137e33397a"),
            Map.entry("007-enforce-employee-relationships", "9:83e13ef473cefd07999bb57e3a7a0ed6"),
            Map.entry("008-create-salary-change", "9:3fccc7b13e6fd2205ead2d8a7e7701af"),
            Map.entry("008-index-salary-history", "9:fbba4014652784a52460ea5f72c1bf3a"),
            Map.entry("009-retire-sample-message", "9:d57dc8e50d5bed9913831a0b39c6db7e"));
    @TempDir Path directory;

    @Test
    void acceptsRecordedLegacyChecksumsWithoutRerunningOrResettingHistory() {
        String url = "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("sql-to-yaml");
        List<Map<String, Object>> before;
        try (var first = application().run(url)) {
            var jdbc = first.getBean(JdbcTemplate.class);
            for (var checksum : LEGACY_CHECKSUMS.entrySet()) {
                assertThat(jdbc.update("UPDATE databasechangelog SET md5sum = ? WHERE id = ? AND author = 'salary-management'",
                        checksum.getValue(), checksum.getKey())).isEqualTo(1);
            }
            jdbc.update("UPDATE employee SET full_name = ? WHERE employee_id = ?", "Preserved SQL-era edit", "EMP-000001");
            before = history(jdbc);
        }
        try (var restarted = application().run(url)) {
            var jdbc = restarted.getBean(JdbcTemplate.class);
            assertThat(history(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee", Integer.class)).isEqualTo(20);
            assertThat(jdbc.queryForObject("SELECT full_name FROM employee WHERE employee_id = ?", String.class, "EMP-000001"))
                    .isEqualTo("Preserved SQL-era edit");
        }
    }

    private List<Map<String, Object>> history(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT id, author, filename, md5sum, dateexecuted, orderexecuted, exectype FROM databasechangelog ORDER BY orderexecuted");
    }

    private SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(SalaryManagementApplication.class)
                .profiles("test").web(WebApplicationType.NONE);
    }
}
