package com.acme.salarymanagement.service;

import com.acme.salarymanagement.bean.CompensationReport;
import com.acme.salarymanagement.bean.ReferenceDataResponse;
import com.acme.salarymanagement.exception.UnimplementedMethodException;
import java.util.List;
import java.util.Map;

public interface DashboardService {
    default ReferenceDataResponse referenceData() { throw new UnimplementedMethodException("Dashboard operation"); }
    default CompensationReport compensation(Map<String, List<String>> filters, List<String> groupBy) {
        throw new UnimplementedMethodException("Dashboard operation");
    }
}
