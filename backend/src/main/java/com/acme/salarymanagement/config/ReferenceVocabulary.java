package com.acme.salarymanagement.config;

import com.acme.salarymanagement.bean.ReferenceDataResponse.Country;
import com.acme.salarymanagement.bean.ReferenceDataResponse.Option;
import java.util.List;

/** Backend-owned canonical vocabulary; seed ordering is retained for repeatable fixtures. */
public final class ReferenceVocabulary {
    private ReferenceVocabulary() { }
    public static final List<String> SEED_COUNTRIES = List.of("IN", "US", "GB", "DE", "SG");
    public static final List<String> SEED_DEPARTMENTS = List.of("Engineering", "Product", "Design", "Finance", "Operations", "HR");
    public static final List<Country> COUNTRIES = List.of(new Country("DE", "Germany"),
            new Country("GB", "United Kingdom"), new Country("IN", "India"),
            new Country("SG", "Singapore"), new Country("US", "United States"));
    public static final List<Option> DEPARTMENTS = SEED_DEPARTMENTS.stream().sorted()
            .map(value -> new Option(value, value.equals("HR") ? "Human Resources" : value)).toList();
    public static final List<Option> LEVELS = java.util.stream.IntStream.rangeClosed(1, 6)
            .mapToObj(level -> new Option("L" + level, "L" + level)).toList();
}
