package com.cryptolab.strategy.domain.extension;

import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.strategy.domain.Signal;
import com.cryptolab.strategy.domain.SignalType;
import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDescriptor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AiDslStrategy implements Strategy {

    public static final String TYPE = "AI_DSL";
    public static final String VERSION = "1.0";
    public static final int MAX_SOURCE_LENGTH = 4_000;
    private static final int MAX_TOKENS = 256;
    private static final int MAX_NODES = 128;
    private static final MathContext MATH = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final String source;
    private final BooleanExpression buyRule;
    private final BooleanExpression sellRule;

    public AiDslStrategy(String source) {
        if (source == null || source.isBlank() || source.length() > MAX_SOURCE_LENGTH) {
            throw new IllegalArgumentException("AI DSL source must contain 1 to 4000 characters");
        }
        this.source = source.trim();
        Parser parser = new Parser(tokenize(this.source));
        parser.expect(TokenType.BUY);
        parser.expect(TokenType.WHEN);
        buyRule = parser.booleanExpression();
        parser.expect(TokenType.SELL);
        parser.expect(TokenType.WHEN);
        sellRule = parser.booleanExpression();
        parser.expect(TokenType.EOF);
    }

    @Override
    public StrategyDescriptor descriptor() {
        return new StrategyDescriptor(TYPE, VERSION, Map.of("source", source));
    }

    @Override
    public Signal analyze(StrategyContext context) {
        Evaluation evaluation = new Evaluation(context.candles().stream()
                .filter(candle -> !candle.openTime().isAfter(context.evaluatedAt()))
                .toList());
        boolean buy = buyRule.evaluate(evaluation) == Truth.TRUE;
        boolean sell = sellRule.evaluate(evaluation) == Truth.TRUE;
        if (buy == sell) {
            String reason = buy
                    ? "AI DSL rules conflicted; returning HOLD"
                    : "AI DSL rules did not match";
            return new Signal(SignalType.HOLD, BigDecimal.ZERO, context.evaluatedAt(), reason);
        }
        return buy
                ? new Signal(SignalType.BUY, BigDecimal.ONE, context.evaluatedAt(), "AI DSL BUY rule matched")
                : new Signal(SignalType.SELL, BigDecimal.ONE.negate(), context.evaluatedAt(),
                        "AI DSL SELL rule matched");
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int cursor = 0;
        while (cursor < source.length()) {
            char current = source.charAt(cursor);
            if (Character.isWhitespace(current)) {
                cursor++;
                continue;
            }
            int start = cursor;
            if (Character.isLetter(current) || current == '_') {
                cursor++;
                while (cursor < source.length()) {
                    char next = source.charAt(cursor);
                    if (!Character.isLetterOrDigit(next) && next != '_') break;
                    cursor++;
                }
                String word = source.substring(start, cursor).toUpperCase(Locale.ROOT);
                try {
                    tokens.add(new Token(TokenType.valueOf(word), word));
                } catch (IllegalArgumentException unsupported) {
                    throw syntax("Unsupported AI DSL word: " + word, start);
                }
            } else if (Character.isDigit(current)
                    || (current == '-' && cursor + 1 < source.length()
                            && Character.isDigit(source.charAt(cursor + 1)))) {
                cursor++;
                boolean decimalPoint = false;
                while (cursor < source.length()) {
                    char next = source.charAt(cursor);
                    if (next == '.' && !decimalPoint) {
                        decimalPoint = true;
                        cursor++;
                    } else if (Character.isDigit(next)) {
                        cursor++;
                    } else {
                        break;
                    }
                }
                String number = source.substring(start, cursor);
                try {
                    new BigDecimal(number);
                } catch (NumberFormatException invalid) {
                    throw syntax("Invalid AI DSL number: " + number, start);
                }
                tokens.add(new Token(TokenType.NUMBER, number));
            } else {
                cursor++;
                switch (current) {
                    case '(' -> tokens.add(new Token(TokenType.LEFT_PAREN, "("));
                    case ')' -> tokens.add(new Token(TokenType.RIGHT_PAREN, ")"));
                    case ',' -> tokens.add(new Token(TokenType.COMMA, ","));
                    case '<' -> {
                        if (cursor < source.length() && source.charAt(cursor) == '=') {
                            cursor++;
                            tokens.add(new Token(TokenType.LTE, "<="));
                        } else {
                            tokens.add(new Token(TokenType.LT, "<"));
                        }
                    }
                    case '>' -> {
                        if (cursor < source.length() && source.charAt(cursor) == '=') {
                            cursor++;
                            tokens.add(new Token(TokenType.GTE, ">="));
                        } else {
                            tokens.add(new Token(TokenType.GT, ">"));
                        }
                    }
                    case '=' -> {
                        if (cursor >= source.length() || source.charAt(cursor) != '=') {
                            throw syntax("AI DSL equality operator is ==", start);
                        }
                        cursor++;
                        tokens.add(new Token(TokenType.EQ, "=="));
                    }
                    case '!' -> {
                        if (cursor >= source.length() || source.charAt(cursor) != '=') {
                            throw syntax("AI DSL inequality operator is !=", start);
                        }
                        cursor++;
                        tokens.add(new Token(TokenType.NEQ, "!="));
                    }
                    default -> throw syntax("Unsupported AI DSL character: " + current, start);
                }
            }
            if (tokens.size() > MAX_TOKENS) {
                throw new IllegalArgumentException("AI DSL source exceeds 256 tokens");
            }
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return List.copyOf(tokens);
    }

    private static IllegalArgumentException syntax(String message, int offset) {
        return new IllegalArgumentException(message + " at offset " + offset);
    }

    private enum TokenType {
        BUY, SELL, WHEN, AND, OR, NOT,
        OPEN, HIGH, LOW, CLOSE, VOLUME,
        SMA, RSI, CHANGE_PCT,
        NUMBER, LEFT_PAREN, RIGHT_PAREN, COMMA,
        LT, LTE, GT, GTE, EQ, NEQ, EOF
    }

    private record Token(TokenType type, String text) {}

    private enum Truth {
        TRUE,
        FALSE,
        UNKNOWN;

        private Truth not() {
            return this == TRUE ? FALSE : this == FALSE ? TRUE : UNKNOWN;
        }

        private Truth and(Truth other) {
            if (this == FALSE || other == FALSE) return FALSE;
            return this == UNKNOWN || other == UNKNOWN ? UNKNOWN : TRUE;
        }

        private Truth or(Truth other) {
            if (this == TRUE || other == TRUE) return TRUE;
            return this == UNKNOWN || other == UNKNOWN ? UNKNOWN : FALSE;
        }
    }

    @FunctionalInterface
    private interface BooleanExpression {
        Truth evaluate(Evaluation evaluation);
    }

    @FunctionalInterface
    private interface NumericExpression {
        BigDecimal evaluate(Evaluation evaluation);
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int cursor;
        private int nodes;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private BooleanExpression booleanExpression() {
            return orExpression();
        }

        private BooleanExpression orExpression() {
            BooleanExpression expression = andExpression();
            while (match(TokenType.OR)) {
                BooleanExpression left = expression;
                BooleanExpression right = andExpression();
                node();
                expression = evaluation -> left.evaluate(evaluation).or(right.evaluate(evaluation));
            }
            return expression;
        }

        private BooleanExpression andExpression() {
            BooleanExpression expression = notExpression();
            while (match(TokenType.AND)) {
                BooleanExpression left = expression;
                BooleanExpression right = notExpression();
                node();
                expression = evaluation -> left.evaluate(evaluation).and(right.evaluate(evaluation));
            }
            return expression;
        }

        private BooleanExpression notExpression() {
            if (match(TokenType.NOT)) {
                BooleanExpression nested = notExpression();
                node();
                return evaluation -> nested.evaluate(evaluation).not();
            }
            if (match(TokenType.LEFT_PAREN)) {
                BooleanExpression nested = booleanExpression();
                expect(TokenType.RIGHT_PAREN);
                return nested;
            }
            return comparison();
        }

        private BooleanExpression comparison() {
            NumericExpression left = numericExpression();
            TokenType operator = current().type();
            if (!match(TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE,
                    TokenType.EQ, TokenType.NEQ)) {
                throw error("Expected a comparison operator");
            }
            NumericExpression right = numericExpression();
            node();
            return evaluation -> {
                BigDecimal leftValue = left.evaluate(evaluation);
                BigDecimal rightValue = right.evaluate(evaluation);
                if (leftValue == null || rightValue == null) return Truth.UNKNOWN;
                int comparison = leftValue.compareTo(rightValue);
                boolean result = switch (operator) {
                    case LT -> comparison < 0;
                    case LTE -> comparison <= 0;
                    case GT -> comparison > 0;
                    case GTE -> comparison >= 0;
                    case EQ -> comparison == 0;
                    case NEQ -> comparison != 0;
                    default -> throw new IllegalStateException("Unexpected comparison operator");
                };
                return result ? Truth.TRUE : Truth.FALSE;
            };
        }

        private NumericExpression numericExpression() {
            if (match(TokenType.NUMBER)) {
                BigDecimal value = new BigDecimal(previous().text());
                node();
                return ignored -> value;
            }
            if (match(TokenType.OPEN, TokenType.HIGH, TokenType.LOW, TokenType.CLOSE, TokenType.VOLUME)) {
                Field field = Field.valueOf(previous().type().name());
                node();
                return evaluation -> evaluation.latest(field);
            }
            if (match(TokenType.SMA)) {
                expect(TokenType.LEFT_PAREN);
                Field field = field();
                expect(TokenType.COMMA);
                int period = period();
                expect(TokenType.RIGHT_PAREN);
                node();
                return evaluation -> evaluation.sma(field, period);
            }
            if (match(TokenType.RSI)) {
                expect(TokenType.LEFT_PAREN);
                int period = period();
                expect(TokenType.RIGHT_PAREN);
                node();
                return evaluation -> evaluation.rsi(period);
            }
            if (match(TokenType.CHANGE_PCT)) {
                expect(TokenType.LEFT_PAREN);
                Field field = field();
                expect(TokenType.COMMA);
                int period = period();
                expect(TokenType.RIGHT_PAREN);
                node();
                return evaluation -> evaluation.changePct(field, period);
            }
            throw error("Expected a number, candle field, or indicator function");
        }

        private Field field() {
            if (!match(TokenType.OPEN, TokenType.HIGH, TokenType.LOW, TokenType.CLOSE, TokenType.VOLUME)) {
                throw error("Expected OPEN, HIGH, LOW, CLOSE, or VOLUME");
            }
            return Field.valueOf(previous().type().name());
        }

        private int period() {
            expect(TokenType.NUMBER);
            try {
                int value = new BigDecimal(previous().text()).intValueExact();
                if (value < 2 || value > 500) {
                    throw new IllegalArgumentException("AI DSL period must be between 2 and 500");
                }
                return value;
            } catch (ArithmeticException invalid) {
                throw new IllegalArgumentException("AI DSL period must be an integer", invalid);
            }
        }

        private void node() {
            nodes++;
            if (nodes > MAX_NODES) {
                throw new IllegalArgumentException("AI DSL source exceeds 128 expression nodes");
            }
        }

        private boolean match(TokenType... expected) {
            for (TokenType type : expected) {
                if (current().type() == type) {
                    cursor++;
                    return true;
                }
            }
            return false;
        }

        private void expect(TokenType expected) {
            if (!match(expected)) {
                throw error("Expected " + expected);
            }
        }

        private Token current() {
            return tokens.get(cursor);
        }

        private Token previous() {
            return tokens.get(cursor - 1);
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " near '" + current().text() + "'");
        }
    }

    private enum Field {
        OPEN,
        HIGH,
        LOW,
        CLOSE,
        VOLUME;

        private BigDecimal value(Candle candle) {
            return switch (this) {
                case OPEN -> candle.open();
                case HIGH -> candle.high();
                case LOW -> candle.low();
                case CLOSE -> candle.close();
                case VOLUME -> candle.volume();
            };
        }
    }

    private static final class Evaluation {
        private final List<Candle> candles;

        private Evaluation(List<Candle> candles) {
            this.candles = candles;
        }

        private BigDecimal latest(Field field) {
            return candles.isEmpty() ? null : field.value(candles.getLast());
        }

        private BigDecimal sma(Field field, int period) {
            if (candles.size() < period) return null;
            BigDecimal sum = BigDecimal.ZERO;
            for (int index = candles.size() - period; index < candles.size(); index++) {
                sum = sum.add(field.value(candles.get(index)));
            }
            return sum.divide(BigDecimal.valueOf(period), MATH);
        }

        private BigDecimal rsi(int period) {
            if (candles.size() <= period) return null;
            BigDecimal gains = BigDecimal.ZERO;
            BigDecimal losses = BigDecimal.ZERO;
            for (int index = candles.size() - period; index < candles.size(); index++) {
                BigDecimal change = candles.get(index).close().subtract(candles.get(index - 1).close());
                if (change.signum() > 0) gains = gains.add(change);
                else if (change.signum() < 0) losses = losses.add(change.abs());
            }
            if (gains.signum() == 0 && losses.signum() == 0) return new BigDecimal("50");
            if (losses.signum() == 0) return HUNDRED;
            if (gains.signum() == 0) return BigDecimal.ZERO;
            BigDecimal relativeStrength = gains.divide(losses, MATH);
            return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(relativeStrength), MATH));
        }

        private BigDecimal changePct(Field field, int period) {
            if (candles.size() <= period) return null;
            BigDecimal current = field.value(candles.getLast());
            BigDecimal reference = field.value(candles.get(candles.size() - 1 - period));
            if (reference.signum() == 0) return BigDecimal.ZERO;
            return current.subtract(reference).divide(reference, MATH).multiply(HUNDRED);
        }
    }
}
