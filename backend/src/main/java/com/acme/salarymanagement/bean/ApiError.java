package com.acme.salarymanagement.bean;

import java.util.List;

public record ApiError(String code, String message, String requestId, List<ApiFieldError> fieldErrors) {}
