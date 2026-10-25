package com.acme.salarymanagement.bean;
public record AuthResponse(UserResponse user, int idleTimeoutSeconds) { }
