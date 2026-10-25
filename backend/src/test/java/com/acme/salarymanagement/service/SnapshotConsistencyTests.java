package com.acme.salarymanagement.service;

import com.acme.salarymanagement.bean.SalaryChangeRequest;
import com.acme.salarymanagement.util.SqlUtility;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties={"spring.h2.console.enabled=false","spring.datasource.url=jdbc:h2:mem:snapshot-consistency;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class SnapshotConsistencyTests {
    @Autowired SalaryService salaries;
    @Autowired DashboardService dashboard;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean SqlUtility sql;
    @BeforeEach void fixture() {
        jdbc.update("DELETE FROM salary_change");
        jdbc.update("UPDATE employee SET employment_status='INACTIVE'");
        jdbc.update("UPDATE employee SET employment_status='ACTIVE',currency_code='INR',department='Engineering',joining_date='2026-01-01',last_employment_date=NULL,initial_salary_effective_from='2026-01-01',salary_effective_from='2026-01-01',initial_annual_base_salary=1200000,annual_base_salary=1200000,version=0 WHERE employee_id='EMP-000001'");
    }
    @Test void reportGroupsAndSummaryUseTheSamePopulationAcrossACommittedWrite() throws Exception {
        var report=interleave("SELECT e.country_code",()->dashboard.compensation(Map.of(),List.of("currency","department")),()->{
            jdbc.update("UPDATE employee SET annual_base_salary=2400000,department='Product',version=version+1 WHERE employee_id='EMP-000001'");
            jdbc.update("UPDATE employee SET employment_status='ACTIVE',currency_code='INR',department='Product',annual_base_salary=1200000 WHERE employee_id='EMP-000002'");
        });
        assertThat(report.totalEmployees()).isEqualTo(1);
        assertThat(report.currencySummaries()).singleElement().satisfies(summary->{
            assertThat(summary.employeeCount()).isEqualTo(1);
            assertThat(summary.totalAnnualBaseSalary()).isEqualTo("1200000.00");
            assertThat(summary.averageAnnualBaseSalary()).isEqualTo("1200000.00");
            assertThat(summary.medianAnnualBaseSalary()).isEqualTo("1200000.00");
        });
        assertThat(report.groups()).singleElement().satisfies(group->{
            assertThat(group.dimensions().get("department")).isEqualTo("Engineering");
            assertThat(group.totalAnnualBaseSalary()).isEqualTo("1200000.00");
            assertThat(group.employeeCount()).isEqualTo(1);
        });
        var fresh=dashboard.compensation(Map.of(),List.of("currency","department"));
        assertThat(fresh.totalEmployees()).isEqualTo(2);
        assertThat(fresh.currencySummaries().getFirst().totalAnnualBaseSalary()).isEqualTo("3600000.00");
        assertThat(fresh.groups().getFirst().dimensions().get("department")).isEqualTo("Product");
    }
    @Test void estimateDoesNotMixOldEmploymentDatesWithNewSalaryHistory() throws Exception {
        var estimate=interleave("SELECT e.*,c.fraction_digits",()->salaries.estimate("EMP-000001",YearMonth.of(2026,10)),()->{
            salaries.change("EMP-000001",new SalaryChangeRequest("2400000.00","Snapshot check",LocalDate.of(2026,10,15),0),
                    UUID.randomUUID().toString(),"maya.rao@acme.example");
            jdbc.update("UPDATE employee SET last_employment_date='2026-10-15',version=version+1 WHERE employee_id='EMP-000001'");
        });
        assertThat(estimate.employeeVersion()).isZero();
        assertThat(estimate.eligibleDays()).isEqualTo(31);
        assertThat(estimate.lastEmploymentDate()).isNull();
        assertThat(estimate.salaryBasis().source()).isEqualTo("INITIAL");
        assertThat(estimate.estimatedBasePay()).isEqualTo("100000.00");
        var fresh=salaries.estimate("EMP-000001",YearMonth.of(2026,10));
        assertThat(fresh.employeeVersion()).isEqualTo(2);
        assertThat(fresh.eligibleDays()).isEqualTo(15);
        assertThat(fresh.salaryBasis().source()).isEqualTo("SALARY_CHANGE");
        assertThat(fresh.estimatedBasePay()).isEqualTo("96774.19");
    }
    // Spy only schedules the interleaving. Every SQL statement and transaction uses real H2.
    private <T> T interleave(String queryPrefix,Callable<T> read,Runnable write) throws Exception {
        var captured=new CountDownLatch(1);
        var committed=new CountDownLatch(1);
        doAnswer(call->{
            Object result=call.callRealMethod();
            if(Thread.currentThread().getName().equals("snapshot-reader") && ((String)call.getArgument(0)).stripLeading().startsWith(queryPrefix)) {
                captured.countDown();
                assertThat(committed.await(10,TimeUnit.SECONDS)).as("writer committed before reader continues").isTrue();
            }
            return result;
        }).when(sql).query(anyString(),any(RowMapper.class),any(Object[].class));
        try(var workers=Executors.newSingleThreadExecutor(r->new Thread(r,"snapshot-reader"))) {
            var result=workers.submit(read);
            try {
                assertThat(captured.await(10,TimeUnit.SECONDS)).as("reader reached its first snapshot query").isTrue();
                new TransactionTemplate(manager).executeWithoutResult(status->write.run());
            } finally { committed.countDown(); }
            return result.get(10,TimeUnit.SECONDS);
        }
    }
}
