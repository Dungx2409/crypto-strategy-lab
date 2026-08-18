package com.cryptolab.experiment.domain;

import com.cryptolab.shared.domain.ImmutableValues;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record SearchParameterSpace(Map<String, Map<String, List<Object>>> values) {

    public SearchParameterSpace {
        if (values == null || values.isEmpty()) {
            values = Map.of();
        } else {
            Map<String, Map<String, List<Object>>> types = new LinkedHashMap<>();
            values.forEach((type, parameters) -> {
                if (type == null || type.isBlank()) {
                    throw new IllegalArgumentException("parameter-space strategy type must not be blank");
                }
                Map<String, List<Object>> parameterCopy = new LinkedHashMap<>();
                if (parameters != null) {
                    parameters.forEach((parameter, choices) -> {
                        if (parameter == null || parameter.isBlank()) {
                            throw new IllegalArgumentException("parameter-space name must not be blank");
                        }
                        if (choices == null || choices.isEmpty() || choices.stream().anyMatch(value -> value == null)) {
                            throw new IllegalArgumentException(
                                    "parameter-space choices must be non-empty and non-null: " + parameter);
                        }
                        HashSet<String> canonicalChoices = new HashSet<>();
                        for (Object choice : choices) {
                            if (!canonicalChoices.add(canonical(choice))) {
                                throw new IllegalArgumentException(
                                        "parameter-space choices must be canonically unique: " + parameter);
                            }
                        }
                        parameterCopy.put(
                                parameter.trim(),
                                choices.stream().map(ImmutableValues::copy).toList());
                    });
                }
                types.put(type.trim().toUpperCase(Locale.ROOT), Map.copyOf(parameterCopy));
            });
            values = Map.copyOf(types);
        }
    }

    public List<Object> choices(String strategyType, String parameter) {
        return values.getOrDefault(strategyType, Map.of()).getOrDefault(parameter, List.of());
    }

    private static String canonical(Object value) {
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
            return "number:" + (decimal.signum() == 0 ? "0" : decimal.toPlainString());
        }
        return value.getClass().getName() + ':' + value;
    }
}
