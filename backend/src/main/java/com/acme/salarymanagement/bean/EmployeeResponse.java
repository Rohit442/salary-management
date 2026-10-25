package com.acme.salarymanagement.bean;

import java.time.Instant;
import java.time.LocalDate;

public record EmployeeResponse(String employeeId, String fullName, String workEmail, String countryCode,
        String department, String jobTitle, String jobLevel, String employmentStatus, String annualBaseSalary,
        String currency, long version, Instant createdAt, Instant updatedAt, LocalDate joiningDate,
        LocalDate lastEmploymentDate, LocalDate initialSalaryEffectiveFrom, LocalDate salaryEffectiveFrom) { }
