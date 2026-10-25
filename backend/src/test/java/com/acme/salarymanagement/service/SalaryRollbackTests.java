package com.acme.salarymanagement.service;

import com.acme.salarymanagement.bean.SalaryChangeRequest;
import com.acme.salarymanagement.exception.ApiRequestException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:salary-rollback;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class SalaryRollbackTests {
    @Autowired SalaryService salaries;
    @Autowired EmployeeService employees;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    // No test-managed transaction: application rollback finishes before the independent read.
    @ParameterizedTest @ValueSource(strings={"salary_change", "employee"})
    void constraintFailureLeavesNoSalaryVersionHistoryOrReplayMetadata(String table) {
        String id=table.equals("employee")?"EMP-000001":"EMP-000002";
        var before=employees.details(id);
        String key=UUID.randomUUID().toString();
        var request=new SalaryChangeRequest("900001.00","Rollback check",LocalDate.of(2026,1,15),before.version());
        String amountColumn=table.equals("employee")?"annual_base_salary":"new_amount";
        jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT forced_salary_failure CHECK ("+amountColumn+" <> 900001"+(table.equals("employee")?" OR employee_id <> '"+id+"'":"")+")");
        try {
            assertThatThrownBy(()->salaries.change(id,request,key,"maya.rao@acme.example"))
                    .isInstanceOfSatisfying(ApiRequestException.class,error->assertThat(error.code()).isEqualTo("SERVICE_UNAVAILABLE"));
            new TransactionTemplate(manager).executeWithoutResult(status->{
                assertThat(employees.details(id)).isEqualTo(before);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change WHERE request_id=?",Integer.class,key)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change WHERE employee_fk=(SELECT id FROM employee WHERE employee_id=?)",Integer.class,id)).isZero();
            });
        } finally {
            jdbc.execute("ALTER TABLE "+table+" DROP CONSTRAINT forced_salary_failure");
        }
        // Failed operations must not consume the key or leave a phantom replay.
        assertThat(salaries.change(id,request,key,"maya.rao@acme.example").replayed()).isFalse();
        assertThat(salaries.change(id,request,key,"maya.rao@acme.example").replayed()).isTrue();
        assertThat(employees.details(id).version()).isEqualTo(before.version()+1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change WHERE request_id=?",Integer.class,key)).isEqualTo(1);
    }
}
