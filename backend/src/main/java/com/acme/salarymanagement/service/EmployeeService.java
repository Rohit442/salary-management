package com.acme.salarymanagement.service;

import com.acme.salarymanagement.bean.EmployeePage;
import com.acme.salarymanagement.bean.EmployeeQuery;
import com.acme.salarymanagement.bean.EmployeeResponse;
import com.acme.salarymanagement.exception.UnimplementedMethodException;

public interface EmployeeService {
    default EmployeePage list(EmployeeQuery query) { throw new UnimplementedMethodException("Employee directory"); }
    default EmployeeResponse details(String employeeId) { throw new UnimplementedMethodException("Employee details"); }
}
