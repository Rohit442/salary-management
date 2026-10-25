package com.acme.salarymanagement.service;

import com.acme.salarymanagement.bean.SalaryChangeRequest;
import com.acme.salarymanagement.exception.ApiRequestException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

@SpringBootTest(properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:salary-concurrency;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class SalaryConcurrencyTests {
    @Autowired SalaryService salaries;
    @Autowired EmployeeService employees;
    @Autowired JdbcTemplate jdbc;
    SalaryChangeRequest request() { return new SalaryChangeRequest("900000.00","Review",LocalDate.of(2026,1,15),0); }
    @Test void duplicateConcurrentRequestsCommitOneChangeAndReplayTheWinner() throws Exception {
        String key=UUID.randomUUID().toString();
        var outcomes=race(()->salaries.change("EMP-000001",request(),key,"maya.rao@acme.example").replayed()?"REPLAY":"CREATED",
                ()->salaries.change("EMP-000001",request(),key,"maya.rao@acme.example").replayed()?"REPLAY":"CREATED");
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED","REPLAY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM salary_change",Integer.class)).isEqualTo(1);
        assertThat(employees.details("EMP-000001").version()).isEqualTo(1);
    }
    @Test void salaryAndInactivationWithSameVersionHaveOnlyOneWinner() throws Exception {
        var outcomes=race(()->{salaries.change("EMP-000002",request(),UUID.randomUUID().toString(),"maya.rao@acme.example");return "OK";},
                ()->{employees.inactivate("EMP-000002",0);return "OK";});
        assertThat(outcomes).containsExactlyInAnyOrder("OK","VERSION_CONFLICT");
        assertThat(employees.details("EMP-000002").version()).isEqualTo(1);
    }
    private List<String> race(Callable<String> first,Callable<String> second) throws Exception {
        var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<String> a=()->run(start,first),b=()->run(start,second);
            var one=workers.submit(a);var two=workers.submit(b);start.countDown();
            return List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));
        }
    }
    private String run(CountDownLatch start,Callable<String> operation) throws Exception {
        start.await();try{return operation.call();}catch(ApiRequestException exception){return exception.code();}
    }
}
