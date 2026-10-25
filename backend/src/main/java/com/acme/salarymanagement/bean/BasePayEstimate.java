package com.acme.salarymanagement.bean;

import java.time.Instant;
import java.time.LocalDate;

public record BasePayEstimate(String employeeId,long employeeVersion,String month,String currency,
        String prorationBasis,LocalDate joiningDate,LocalDate lastEmploymentDate,LocalDate salaryHistoryFrom,
        int daysInMonth,int eligibleDays,SalaryBasis salaryBasis,String estimatedBasePay,
        boolean isFutureMonth,LocalDate asOfDate,Instant generatedAt) {
    public record SalaryBasis(String source,String changeId,LocalDate effectiveDate,String annualBaseSalary,String monthlyBasePay) { }
}
