package com.acme.salarymanagement.sample;

public class SampleReadException extends RuntimeException {
    public SampleReadException(Throwable cause) {
        super("Unable to read the sample message", cause);
    }
}
