package com.acme.salarymanagement.bean;

import java.util.List;

public record ApiError(String code, String message, String requestId, List<ApiFieldError> fieldErrors,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.util.Map<String,Long> details) {
    public ApiError(String code,String message,String requestId,List<ApiFieldError> fieldErrors) {
        this(code,message,requestId,fieldErrors,null);
    }
}
