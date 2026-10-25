package com.acme.salarymanagement.service;

import com.acme.salarymanagement.bean.CreateEmployeeRequest;
import com.acme.salarymanagement.exception.ApiRequestException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:employee-concurrency;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class EmployeeConcurrencyTests {
    @Autowired EmployeeService service;
    @Autowired JdbcTemplate jdbc;
    @Test void concurrentCreatesCommitOnlyOneEmployee() throws Exception {
        var request=new CreateEmployeeRequest("EMP-099999","Concurrent Employee","concurrent@acme.example","IN",
                "Engineering","Engineer","L3","960000.00","INR",LocalDate.of(2026,1,1),LocalDate.of(2026,1,1));
        assertThat(race(()->service.create(request,"maya.rao@acme.example"))).containsExactlyInAnyOrder("OK","EMPLOYEE_ID_EXISTS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE employee_id='EMP-099999'",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentTransitionsHaveOneWinnerAndOneStaleVersion() throws Exception {
        assertThat(race(()->service.inactivate("EMP-000001",0))).containsExactlyInAnyOrder("OK","VERSION_CONFLICT");
        assertThat(service.details("EMP-000001").version()).isEqualTo(1);
    }
    private List<String> race(Callable<?> operation) throws Exception {
        var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<String> task=()-> { start.await(); try { operation.call(); return "OK"; }
                catch(ApiRequestException exception) { return exception.code(); } };
            var first=workers.submit(task); var second=workers.submit(task);start.countDown();
            return List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
        }
    }
}
