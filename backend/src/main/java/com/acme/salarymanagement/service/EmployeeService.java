package com.acme.salarymanagement.service;

import com.acme.salarymanagement.bean.CreateEmployeeRequest;
import com.acme.salarymanagement.bean.EmployeeDetailsRequest;
import com.acme.salarymanagement.bean.EmployeePage;
import com.acme.salarymanagement.bean.EmployeeQuery;
import com.acme.salarymanagement.bean.EmployeeResponse;
import com.acme.salarymanagement.exception.UnimplementedMethodException;

public interface EmployeeService {
    default EmployeeResponse create(CreateEmployeeRequest request,String actorEmail) { throw new UnimplementedMethodException("Create employee"); }
    default EmployeeResponse updateDetails(String id,EmployeeDetailsRequest request) { throw new UnimplementedMethodException("Update employee details"); }
    default EmployeeResponse inactivate(String id,long version) { throw new UnimplementedMethodException("Inactivate employee"); }
    default EmployeePage list(EmployeeQuery query) { throw new UnimplementedMethodException("Employee directory"); }
    default EmployeeResponse details(String employeeId) { throw new UnimplementedMethodException("Employee details"); }
}
