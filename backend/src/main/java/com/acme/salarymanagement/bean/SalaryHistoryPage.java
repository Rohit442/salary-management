package com.acme.salarymanagement.bean;
import java.util.List;
public record SalaryHistoryPage(String employeeId,List<SalaryChangeResponse> items,int page,int size,
        int numberOfElements,long totalElements,long totalPages,EmployeePage.Sort sort,boolean hasPrevious,boolean hasNext) { }
