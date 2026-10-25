package com.acme.salarymanagement.bean;
import java.time.LocalDate;
public record SalaryChangeRequest(String annualBaseSalary,String reason,LocalDate effectiveDate,long version) { }
