package com.acme.salarymanagement.sample;

import com.acme.salarymanagement.shared.SqlUtility;
import org.springframework.stereotype.Service;

@Service
public class SampleServiceImpl implements SampleService {
    private final SqlUtility sql;

    public SampleServiceImpl(SqlUtility sql) {
        this.sql = sql;
    }

    @Override
    public SampleMessage getMessage() {
        return sql.queryOne("SELECT id, message FROM sample_message WHERE id = ?",
                (row, index) -> new SampleMessage(row.getLong("id"), row.getString("message")), 1L);
    }
}
