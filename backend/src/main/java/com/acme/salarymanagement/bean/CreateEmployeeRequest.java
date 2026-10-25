package com.acme.salarymanagement.bean;
import java.time.LocalDate;
public record CreateEmployeeRequest(String employeeId, String fullName, String workEmail, String countryCode,
        String department, String jobTitle, String jobLevel, String annualBaseSalary, String currency,
        LocalDate joiningDate, LocalDate initialSalaryEffectiveFrom) { }
