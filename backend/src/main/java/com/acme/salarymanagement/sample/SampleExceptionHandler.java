package com.acme.salarymanagement.sample;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SampleController.class)
public class SampleExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleExceptionHandler.class);

    @ExceptionHandler(SampleReadException.class)
    public ResponseEntity<ProblemDetail> handleReadFailure(SampleReadException exception) {
        String requestId = UUID.randomUUID().toString();
        LOGGER.error("Sample API read failed; requestId={}", requestId, exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read the sample message.");
        problem.setProperty("requestId", requestId);
        return ResponseEntity.internalServerError().header("X-Request-ID", requestId).body(problem);
    }
}
