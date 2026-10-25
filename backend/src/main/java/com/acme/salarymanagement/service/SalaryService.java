package com.acme.salarymanagement.service;
import com.acme.salarymanagement.bean.*;
import com.acme.salarymanagement.exception.UnimplementedMethodException;
public interface SalaryService {
    default SalaryChangeResult change(String id,SalaryChangeRequest request,String key,String email) { throw new UnimplementedMethodException("Salary change"); }
    default SalaryHistoryPage history(String id,int page,int size) { throw new UnimplementedMethodException("Salary history"); }
}
