package com.acme.salarymanagement.sample;

import com.acme.salarymanagement.shared.UnimplementedMethodException;

public interface SampleService {
    default SampleMessage getMessage() {
        throw new UnimplementedMethodException("getMessage");
    }
}
