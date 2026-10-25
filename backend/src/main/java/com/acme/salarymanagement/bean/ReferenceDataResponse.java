package com.acme.salarymanagement.bean;

import java.util.List;

public record ReferenceDataResponse(List<Country> countries, List<Option> departments,
        List<Option> jobLevels, List<Currency> currencies) {
    public record Country(String code, String label) { }
    public record Option(String value, String label) { }
    public record Currency(String code, String label, int fractionDigits) { }
}
