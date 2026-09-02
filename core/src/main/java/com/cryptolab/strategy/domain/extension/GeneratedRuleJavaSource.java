package com.cryptolab.strategy.domain.extension;

import com.cryptolab.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public final class GeneratedRuleJavaSource {

    public static final String CLASS_NAME_PARAMETER = "generatedClassName";
    public static final String SOURCE_PARAMETER = "generatedJavaSource";

    private GeneratedRuleJavaSource() {}

    public static StrategyDefinition enrich(StrategyDefinition definition) {
        if (!RuleBasedStrategy.TYPE.equals(definition.type())) return definition;
        Generated generated = generate(definition);
        Map<String, Object> parameters = new HashMap<>(definition.parameters());
        parameters.put(CLASS_NAME_PARAMETER, generated.className());
        parameters.put(SOURCE_PARAMETER, generated.source());
        return new StrategyDefinition(definition.type(), definition.version(), parameters);
    }

    public static Generated generate(StrategyDefinition definition) {
        if (!RuleBasedStrategy.TYPE.equals(definition.type())
                || !RuleBasedStrategy.VERSION.equals(definition.version())) {
            throw new IllegalArgumentException("generated Java requires RULE@1.0");
        }
        Map<String, Object> values = definition.parameters();
        RuleBasedStrategy.Metric buyMetric = metric(values, "buyMetric", "RSI");
        int buyPeriod = period(values, "buyPeriod", 14);
        RuleBasedStrategy.Operator buyOperator = operator(values, "buyOperator", "LTE");
        BigDecimal buyThreshold = decimal(values, "buyThreshold", "30");
        RuleBasedStrategy.Metric sellMetric = metric(values, "sellMetric", "RSI");
        int sellPeriod = period(values, "sellPeriod", 14);
        RuleBasedStrategy.Operator sellOperator = operator(values, "sellOperator", "GTE");
        BigDecimal sellThreshold = decimal(values, "sellThreshold", "70");
        String canonical = String.join("|", buyMetric.name(), String.valueOf(buyPeriod),
                buyOperator.name(), buyThreshold.toPlainString(), sellMetric.name(),
                String.valueOf(sellPeriod), sellOperator.name(), sellThreshold.toPlainString());
        String simpleName = "GeneratedRule_" + sha256(canonical).substring(0, 16);
        String className = "com.cryptolab.generated." + simpleName;
        String source = """
                package com.cryptolab.generated;

                import com.cryptolab.strategy.domain.Signal;
                import com.cryptolab.strategy.domain.StrategyContext;
                import com.cryptolab.strategy.domain.Strategy;
                import com.cryptolab.strategy.domain.extension.GeneratedRuleLogic;
                import com.cryptolab.strategy.domain.extension.RuleBasedStrategy;
                import java.math.BigDecimal;

                public final class %s implements GeneratedRuleLogic {
                    private final Strategy delegate = new RuleBasedStrategy(
                            RuleBasedStrategy.Metric.%s, %d,
                            RuleBasedStrategy.Operator.%s, new BigDecimal("%s"),
                            RuleBasedStrategy.Metric.%s, %d,
                            RuleBasedStrategy.Operator.%s, new BigDecimal("%s"));

                    @Override
                    public Signal analyze(StrategyContext context) {
                        return delegate.analyze(context);
                    }
                }
                """.formatted(simpleName, buyMetric, buyPeriod, buyOperator,
                buyThreshold.toPlainString(), sellMetric, sellPeriod, sellOperator,
                sellThreshold.toPlainString());
        return new Generated(className, source);
    }

    private static RuleBasedStrategy.Metric metric(
            Map<String, Object> values, String name, String fallback) {
        try {
            return RuleBasedStrategy.Metric.valueOf(
                    String.valueOf(values.getOrDefault(name, fallback)).toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(name + " is not a supported generated metric", invalid);
        }
    }

    private static RuleBasedStrategy.Operator operator(
            Map<String, Object> values, String name, String fallback) {
        try {
            return RuleBasedStrategy.Operator.valueOf(
                    String.valueOf(values.getOrDefault(name, fallback)).toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(name + " is not a supported generated operator", invalid);
        }
    }

    private static int period(Map<String, Object> values, String name, int fallback) {
        try {
            int value = new BigDecimal(String.valueOf(values.getOrDefault(name, fallback)))
                    .intValueExact();
            if (value < 2 || value > 500) throw new ArithmeticException();
            return value;
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " must be an integer between 2 and 500", invalid);
        }
    }

    private static BigDecimal decimal(
            Map<String, Object> values, String name, String fallback) {
        try {
            return new BigDecimal(String.valueOf(values.getOrDefault(name, fallback)));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " must be a decimal number", invalid);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record Generated(String className, String source) {}
}
