package com.acme.salarymanagement.bean;
import java.util.List;
public record UserResponse(String id, String email, String displayName, List<String> roles) { }
