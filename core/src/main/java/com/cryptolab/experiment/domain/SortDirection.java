package com.cryptolab.experiment.domain;

import java.util.Locale;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection parse(String value) {
        if (value == null || value.isBlank()) {
            return DESC;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ASC", "ASCENDING" -> ASC;
            case "DESC", "DESCENDING" -> DESC;
            default -> throw new IllegalArgumentException("unsupported sort direction: " + value);
        };
    }
}
