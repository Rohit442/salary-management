package com.acme.salarymanagement.bean;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
public record SalaryHistoryPage(String employeeId,List<SalaryChangeResponse> items,int page,int size,
        int numberOfElements,long totalElements,long totalPages,EmployeePage.Sort sort,boolean hasPrevious,boolean hasNext,
        InitialSalary initialSalary) {
    public record InitialSalary(String annualBaseSalary,String currency,LocalDate effectiveDate,Instant recordedAt,
            SalaryChangeResponse.Actor actor) { }
}
