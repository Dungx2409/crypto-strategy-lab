package com.cryptolab.experiment.domain;

import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class CandidateCanonicalizer {

    private CandidateCanonicalizer() {}

    public static String hash(
            List<StrategyDefinition> strategies,
            CombinationPolicyDefinition combinationPolicy) {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("strategies must not be empty");
        }
        if (combinationPolicy == null) {
            throw new IllegalArgumentException("combinationPolicy must not be null");
        }
        List<String> canonicalStrategies = new ArrayList<>();
        for (StrategyDefinition strategy : strategies) {
            canonicalStrategies.add(canonicalStrategy(strategy));
        }
        canonicalStrategies.sort(Comparator.naturalOrder());

        String canonical = "strategies=" + canonicalStrategies
                + ";policy=" + text(combinationPolicy.type())
                + '@' + text(combinationPolicy.version())
                + ";weights=" + canonicalMap(combinationPolicy.weights())
                + ";threshold=" + canonicalValue(combinationPolicy.threshold());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static void verify(CandidateStrategy candidate) {
        String expected = hash(candidate.strategies(), candidate.combinationPolicy());
        if (!expected.equals(candidate.candidateHash())) {
            throw new IllegalArgumentException("candidateHash does not match canonical candidate configuration");
        }
    }

    private static String canonicalStrategy(StrategyDefinition strategy) {
        return text(strategy.type()) + '@' + text(strategy.version()) + canonicalMap(strategy.parameters());
    }

    private static String canonicalMap(Map<?, ?> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder result = new StringBuilder("{");
        values.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(entry -> result.append(text(String.valueOf(entry.getKey())))
                        .append(':')
                        .append(canonicalValue(entry.getValue()))
                        .append(','));
        return result.append('}').toString();
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof BigDecimal decimal) {
            BigDecimal normalized = decimal.stripTrailingZeros();
            return normalized.signum() == 0 ? "0" : normalized.toPlainString();
        }
        if (value instanceof Number number) {
            return canonicalValue(new BigDecimal(number.toString()));
        }
        if (value instanceof Map<?, ?> map) {
            return canonicalMap(map);
        }
        if (value instanceof List<?> list) {
            StringBuilder result = new StringBuilder("[");
            list.forEach(item -> result.append(canonicalValue(item)).append(','));
            return result.append(']').toString();
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }
        return text(value.toString());
    }

    private static String text(String value) {
        return value.length() + ":" + value;
    }
}
