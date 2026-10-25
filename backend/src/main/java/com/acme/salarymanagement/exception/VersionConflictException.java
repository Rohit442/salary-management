package com.acme.salarymanagement.exception;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
public class VersionConflictException extends ApiRequestException {
    private final Map<String,Long> details;
    public VersionConflictException(long expected,long current) {
        super(HttpStatus.CONFLICT,"VERSION_CONFLICT","This employee changed. Review the latest record before saving again.",List.of());
        details=Map.of("expectedVersion",expected,"currentVersion",current);
    }
    public Map<String,Long> details() { return details; }
}
