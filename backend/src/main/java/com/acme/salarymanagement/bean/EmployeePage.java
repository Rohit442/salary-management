package com.acme.salarymanagement.bean;

import java.util.List;

public record EmployeePage(List<EmployeeResponse> items, int page, int size, int numberOfElements,
        long totalElements, long totalPages, Sort sort, boolean hasPrevious, boolean hasNext) {
    public record Sort(String field, String direction) { }
}
