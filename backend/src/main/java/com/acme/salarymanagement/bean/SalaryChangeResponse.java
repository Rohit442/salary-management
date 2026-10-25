package com.acme.salarymanagement.bean;
import java.time.Instant;
import java.time.LocalDate;
public record SalaryChangeResponse(String changeId,String employeeId,String previousAmount,String newAmount,
        String currency,String reason,Actor actor,Instant changedAt,long resultingEmployeeVersion,LocalDate effectiveDate) {
    public record Actor(String id,String displayName) { }
}
