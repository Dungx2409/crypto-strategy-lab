package com.cryptolab.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ImmutableValues {

    private ImmutableValues() {}

    public static Map<String, Object> copyMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copy(value)));
        return Collections.unmodifiableMap(copy);
    }

    public static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> copy.put(String.valueOf(key), copy(nestedValue)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copy(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
