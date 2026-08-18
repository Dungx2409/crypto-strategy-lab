package com.cryptolab.experiment.domain;

import com.cryptolab.shared.domain.ImmutableValues;
import java.util.Map;
import java.util.Locale;

public record GeneratorSnapshot(
        String type,
        String version,
        Map<String, Object> configuration,
        Long randomSeed) {

    public GeneratorSnapshot {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("generator type must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("generator version must not be blank");
        }
        type = type.trim();
        version = version.trim();
        configuration = ImmutableValues.copyMap(configuration);
        String normalizedType = type.toUpperCase(Locale.ROOT);
        if ((normalizedType.equals("RANDOM") || normalizedType.equals("GENETIC")) && randomSeed == null) {
            throw new IllegalArgumentException("randomSeed is required for random or genetic generators");
        }
    }
}
