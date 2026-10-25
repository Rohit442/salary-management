package com.acme.salarymanagement.sample;

import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleController {
    private final SampleService service;

    public SampleController(SampleService service) {
        this.service = service;
    }

    @GetMapping("/api/sample")
    public ResponseEntity<SampleMessage> getMessage() {
        List<String> errors = List.of();
        try {
            // This GET accepts no request fields requiring validation.
            if (errors.isEmpty()) {
                SampleMessage response = service.getMessage();
                return ResponseEntity.ok(response);
            }
            throw new IllegalArgumentException(String.join(", ", errors));
        } catch (DataAccessException exception) {
            throw new SampleReadException(exception);
        }
    }
}
