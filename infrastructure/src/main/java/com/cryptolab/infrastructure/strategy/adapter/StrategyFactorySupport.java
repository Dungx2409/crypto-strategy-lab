package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

final class StrategyFactorySupport {

    private StrategyFactorySupport() {}

    static Map<String, Object> validate(
            StrategyDefinition definition,
            String expectedType,
            String expectedVersion,
            Set<String> supportedParameters) {
        if (!expectedType.equalsIgnoreCase(definition.type())) {
            throw new IllegalArgumentException("factory " + expectedType + " cannot create type " + definition.type());
        }
        if (!expectedVersion.equals(definition.version())) {
            throw new IllegalArgumentException(
                    "unsupported " + expectedType + " strategy version: " + definition.version());
        }
        for (String parameter : definition.parameters().keySet()) {
            if (!supportedParameters.contains(parameter)) {
                throw new IllegalArgumentException("unknown " + expectedType + " parameter: " + parameter);
            }
        }
        return definition.parameters();
    }

    static int integer(Map<String, Object> parameters, String name, int defaultValue) {
        Object value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.toString());
            return decimal.intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    static BigDecimal decimal(Map<String, Object> parameters, String name, String defaultValue) {
        Object value = parameters.get(name);
        try {
            return value == null ? new BigDecimal(defaultValue) : new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a decimal number", exception);
        }
    }
}
