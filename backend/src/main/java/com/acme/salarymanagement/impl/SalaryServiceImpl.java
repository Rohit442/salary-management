package com.acme.salarymanagement.impl;

import com.acme.salarymanagement.bean.*;
import com.acme.salarymanagement.exception.ApiRequestException;
import com.acme.salarymanagement.exception.VersionConflictException;
import com.acme.salarymanagement.service.SalaryService;
import com.acme.salarymanagement.util.SqlUtility;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SalaryServiceImpl implements SalaryService {
    private record Actor(long id,String publicId,String name) { }
    private record Employee(long id,String employeeId,String currency,int precision,String status,long version,
            BigDecimal salary,LocalDate joining,LocalDate leaving,BigDecimal initialSalary,LocalDate initialEffective,LocalDate effective) { }
    private record Stored(SalaryChangeResponse response,String fingerprint) { }
    private static final String CHANGES="""
            SELECT s.*,e.employee_id,a.public_id AS actor_public_id,c.fraction_digits
            FROM salary_change s JOIN employee e ON e.id=s.employee_fk
            JOIN hr_user a ON a.id=s.actor_fk JOIN currency c ON c.code=s.currency_code
            """;
    private final SqlUtility sql;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public SalaryServiceImpl(SqlUtility sql,Clock clock,PlatformTransactionManager manager) {
        this.sql=sql;this.clock=clock;this.transactions=new TransactionTemplate(manager);
    }
    @Override
    public SalaryChangeResult change(String id,SalaryChangeRequest request,String key,String email) {
        String fingerprint=fingerprint(id,request);
        try {
            return transactions.execute(status->apply(id,request,key,email,fingerprint));
        } catch(DuplicateKeyException exception) {
            try {
                var replay=transactions.execute(status->replay(actor(email),key,fingerprint));
                if(replay!=null) return replay;
            } catch(DataAccessException dependency) { throw unavailable(dependency); }
            throw unavailable(exception);
        } catch(DataAccessException exception) { throw unavailable(exception); }
    }
    private SalaryChangeResult apply(String id,SalaryChangeRequest request,String key,String email,String fingerprint) {
        Actor actor=actor(email);
        var existing=replay(actor,key,fingerprint);
        if(existing!=null) return existing;
        Employee employee=employee(id,true);
        // Another transaction can commit the same operation while this request waits for the employee lock.
        existing=replay(actor,key,fingerprint);
        if(existing!=null) return existing;
        if(employee.version()!=request.version()) throw new VersionConflictException(request.version(),employee.version());
        if(!employee.status().equals("ACTIVE")) throw conflict("EMPLOYEE_INACTIVE","Salary cannot be changed for an inactive employee.");
        BigDecimal amount=new BigDecimal(request.annualBaseSalary());
        if(amount.scale()>employee.precision()) throw invalid("annualBaseSalary","INVALID_PRECISION","Salary exceeds the currency precision.");
        if(amount.compareTo(employee.salary())==0) throw invalid("annualBaseSalary","SALARY_UNCHANGED","The new salary must differ from the current salary.");
        if(!request.effectiveDate().isAfter(employee.effective())) throw invalid("effectiveDate","EFFECTIVE_DATE_NOT_AFTER_LATEST","Effective date must follow the latest salary date.");
        if(request.effectiveDate().isBefore(employee.joining()) || (employee.leaving()!=null && request.effectiveDate().isAfter(employee.leaving())))
            throw invalid("effectiveDate","INVALID_DATE_ORDER","Effective date must fall within employment dates.");
        OffsetDateTime now=OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        sql.update("""
                INSERT INTO salary_change(public_id,employee_fk,actor_fk,actor_display_name,previous_amount,new_amount,
                currency_code,reason,effective_date,changed_at,resulting_version,request_id,request_fingerprint)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID().toString(),employee.id(),actor.id(),actor.name(),employee.salary(),amount,employee.currency(),
                request.reason(),request.effectiveDate(),now,employee.version()+1,key,fingerprint);
        int changed=sql.update("UPDATE employee SET annual_base_salary=?,salary_effective_from=?,version=version+1,updated_at=? WHERE id=? AND version=?",
                amount,request.effectiveDate(),now,employee.id(),request.version());
        if(changed!=1) throw new VersionConflictException(request.version(),employee.version());
        return new SalaryChangeResult(stored(actor.id(),key).getFirst().response(),false);
    }
    private Actor actor(String email) {
        var actors=sql.query("SELECT id,public_id,display_name FROM hr_user WHERE work_email=? AND enabled=true AND role='HR'",
                (row,index)->new Actor(row.getLong("id"),row.getString("public_id"),row.getString("display_name")),email);
        if(actors.isEmpty()) throw new ApiRequestException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","Please sign in to access the HR workspace.",List.of());
        return actors.getFirst();
    }
    private SalaryChangeResult replay(Actor actor,String key,String fingerprint) {
        var changes=stored(actor.id(),key);
        if(changes.isEmpty()) return null;
        var original=changes.getFirst();
        if(!original.fingerprint().equals(fingerprint)) throw conflict("IDEMPOTENCY_KEY_REUSED","This operation key was already used for another salary request.");
        return new SalaryChangeResult(original.response(),true);
    }
    private List<Stored> stored(long actorId,String key) {
        return sql.query(CHANGES+" WHERE s.actor_fk=? AND s.request_id=?",(row,index)->new Stored(change(row,index),row.getString("request_fingerprint")),actorId,key);
    }
    private String fingerprint(String id,SalaryChangeRequest request) {
        String canonical=String.join("\n",id,new BigDecimal(request.annualBaseSalary()).stripTrailingZeros().toPlainString(),request.effectiveDate().toString(),request.reason(),Long.toString(request.version()));
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private Employee employee(String id,boolean lock) {
        var rows=sql.query("""
                SELECT e.*,c.fraction_digits FROM employee e JOIN currency c ON c.code=e.currency_code WHERE e.employee_id=?
                """+(lock?" FOR UPDATE":""),(row,index)->new Employee(row.getLong("id"),row.getString("employee_id"),row.getString("currency_code"),
                row.getInt("fraction_digits"),row.getString("employment_status"),row.getLong("version"),row.getBigDecimal("annual_base_salary"),
                row.getObject("joining_date",LocalDate.class),row.getObject("last_employment_date",LocalDate.class),row.getBigDecimal("initial_annual_base_salary"),
                row.getObject("initial_salary_effective_from",LocalDate.class),row.getObject("salary_effective_from",LocalDate.class)),id);
        if(rows.isEmpty()) throw new ApiRequestException(HttpStatus.NOT_FOUND,"EMPLOYEE_NOT_FOUND","Employee was not found.",List.of());
        return rows.getFirst();
    }
    @Override
    @Transactional(readOnly=true,isolation=Isolation.SERIALIZABLE)
    public SalaryHistoryPage history(String id,int page,int size) {
        try {
            Employee employee=employee(id,false);
            long total=sql.queryOne("SELECT COUNT(*) FROM salary_change WHERE employee_fk=?",(row,index)->row.getLong(1),employee.id());
            var items=sql.query(CHANGES+" WHERE s.employee_fk=? ORDER BY s.changed_at DESC,s.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",this::change,employee.id(),(long)page*size,size);
            long pages=(total+size-1)/size;
            return new SalaryHistoryPage(id,items,page,size,items.size(),total,pages,new EmployeePage.Sort("changedAt","desc"),page>0,page+1L<pages);
        } catch(DataAccessException exception) { throw unavailable(exception); }
    }
    private SalaryChangeResponse change(ResultSet row,int index) throws SQLException {
        int precision=row.getInt("fraction_digits");
        return new SalaryChangeResponse("chg-"+row.getString("public_id"),row.getString("employee_id"),money(row.getBigDecimal("previous_amount"),precision),
                money(row.getBigDecimal("new_amount"),precision),row.getString("currency_code"),row.getString("reason"),
                new SalaryChangeResponse.Actor(row.getString("actor_public_id"),row.getString("actor_display_name")),
                row.getObject("changed_at",OffsetDateTime.class).toInstant(),row.getLong("resulting_version"),row.getObject("effective_date",LocalDate.class));
    }
    private String money(BigDecimal amount,int precision) { return amount.setScale(precision,RoundingMode.HALF_UP).toPlainString(); }
    private ApiRequestException invalid(String field,String code,String message) { return new ApiRequestException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message,List.of(new ApiFieldError(field,code,message))); }
    private ApiRequestException conflict(String code,String message) { return new ApiRequestException(HttpStatus.CONFLICT,code,message,List.of()); }
    private ApiRequestException unavailable(Exception cause) { return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Salary data is temporarily unavailable. Retain the same operation key for an uncertain retry.",List.of(),cause); }
}
