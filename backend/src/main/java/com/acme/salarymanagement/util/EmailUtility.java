package com.acme.salarymanagement.util;

import java.util.Locale;

public final class EmailUtility {
    private EmailUtility() { }
    public static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
