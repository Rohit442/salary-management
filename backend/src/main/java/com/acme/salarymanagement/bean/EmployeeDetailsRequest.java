package com.acme.salarymanagement.bean;
import java.time.LocalDate;
public record EmployeeDetailsRequest(String fullName, String workEmail, String countryCode, String department,
        String jobTitle, String jobLevel, LocalDate joiningDate, LocalDate lastEmploymentDate, long version) { }
