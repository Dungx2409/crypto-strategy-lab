package com.cryptolab.strategy.domain.extension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AiDslParser {

    private static final int MAX_TOKENS = 256;
    private static final int MAX_NODES = 128;

    private final List<AiDslToken> tokens;
    private int cursor;
    private int nodes;
    private AiDslBooleanExpression buyRule;
    private AiDslBooleanExpression sellRule;

    private AiDslParser(List<AiDslToken> tokens) {
        this.tokens = tokens;
    }

    static AiDslParser parse(String source) {
        AiDslParser parser = new AiDslParser(tokenize(source));
        parser.expect(AiDslTokenType.BUY);
        parser.expect(AiDslTokenType.WHEN);
        parser.buyRule = parser.booleanExpression();
        parser.expect(AiDslTokenType.SELL);
        parser.expect(AiDslTokenType.WHEN);
        parser.sellRule = parser.booleanExpression();
        parser.expect(AiDslTokenType.EOF);
        return parser;
    }

    AiDslBooleanExpression buyRule() {
        return buyRule;
    }

    AiDslBooleanExpression sellRule() {
        return sellRule;
    }

    private static List<AiDslToken> tokenize(String source) {
        List<AiDslToken> tokens = new ArrayList<>();
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
                    if (!Character.isLetterOrDigit(next) && next != '_') {
                        break;
                    }
                    cursor++;
                }
                String word = source.substring(start, cursor).toUpperCase(Locale.ROOT);
                try {
                    tokens.add(new AiDslToken(AiDslTokenType.valueOf(word), word));
                } catch (IllegalArgumentException unknown) {
                    throw syntax("Unsupported AI DSL word: " + word, start);
                }
            } else if (Character.isDigit(current)
                    || (current == '.' && cursor + 1 < source.length() && Character.isDigit(source.charAt(cursor + 1)))
                    || (current == '-' && cursor + 1 < source.length() && Character.isDigit(source.charAt(cursor + 1)))) {
                cursor++;
                boolean decimalPoint = current == '.';
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
                tokens.add(new AiDslToken(AiDslTokenType.NUMBER, number));
            } else {
                cursor++;
                switch (current) {
                    case '(' -> tokens.add(new AiDslToken(AiDslTokenType.LEFT_PAREN, "("));
                    case ')' -> tokens.add(new AiDslToken(AiDslTokenType.RIGHT_PAREN, ")"));
                    case ',' -> tokens.add(new AiDslToken(AiDslTokenType.COMMA, ","));
                    case '<' -> {
                        if (cursor < source.length() && source.charAt(cursor) == '=') {
                            cursor++;
                            tokens.add(new AiDslToken(AiDslTokenType.LTE, "<="));
                        } else {
                            tokens.add(new AiDslToken(AiDslTokenType.LT, "<"));
                        }
                    }
                    case '>' -> {
                        if (cursor < source.length() && source.charAt(cursor) == '=') {
                            cursor++;
                            tokens.add(new AiDslToken(AiDslTokenType.GTE, ">="));
                        } else {
                            tokens.add(new AiDslToken(AiDslTokenType.GT, ">"));
                        }
                    }
                    case '=' -> {
                        if (cursor >= source.length() || source.charAt(cursor) != '=') {
                            throw syntax("AI DSL equality operator is ==", start);
                        }
                        cursor++;
                        tokens.add(new AiDslToken(AiDslTokenType.EQ, "=="));
                    }
                    case '!' -> {
                        if (cursor >= source.length() || source.charAt(cursor) != '=') {
                            throw syntax("AI DSL inequality operator is !=", start);
                        }
                        cursor++;
                        tokens.add(new AiDslToken(AiDslTokenType.NEQ, "!="));
                    }
                    default -> throw syntax("Unsupported AI DSL character: " + current, start);
                }
            }

            if (tokens.size() > MAX_TOKENS) {
                throw new IllegalArgumentException("AI DSL source exceeds 256 tokens");
            }
        }
        tokens.add(new AiDslToken(AiDslTokenType.EOF, ""));
        return List.copyOf(tokens);
    }

    private AiDslBooleanExpression booleanExpression() {
        return orExpression();
    }

    private AiDslBooleanExpression orExpression() {
        AiDslBooleanExpression expression = andExpression();
        while (match(AiDslTokenType.OR)) {
            AiDslBooleanExpression left = expression;
            AiDslBooleanExpression right = andExpression();
            node();
            expression = evaluation -> left.evaluate(evaluation).or(right.evaluate(evaluation));
        }
        return expression;
    }

    private AiDslBooleanExpression andExpression() {
        AiDslBooleanExpression expression = notExpression();
        while (match(AiDslTokenType.AND)) {
            AiDslBooleanExpression left = expression;
            AiDslBooleanExpression right = notExpression();
            node();
            expression = evaluation -> left.evaluate(evaluation).and(right.evaluate(evaluation));
        }
        return expression;
    }

    private AiDslBooleanExpression notExpression() {
        if (match(AiDslTokenType.NOT)) {
            AiDslBooleanExpression nested = notExpression();
            node();
            return evaluation -> nested.evaluate(evaluation).not();
        }
        if (match(AiDslTokenType.LEFT_PAREN)) {
            AiDslBooleanExpression nested = booleanExpression();
            expect(AiDslTokenType.RIGHT_PAREN);
            return nested;
        }
        return comparison();
    }

    private AiDslBooleanExpression comparison() {
        AiDslNumericExpression left = numericExpression();
        AiDslTokenType operator = current().type();
        if (!match(AiDslTokenType.LT, AiDslTokenType.LTE, AiDslTokenType.GT, AiDslTokenType.GTE,
                AiDslTokenType.EQ, AiDslTokenType.NEQ)) {
            throw error("Expected a comparison operator");
        }
        AiDslNumericExpression right = numericExpression();
        node();
        return evaluation -> {
            BigDecimal leftValue = left.evaluate(evaluation);
            BigDecimal rightValue = right.evaluate(evaluation);
            if (leftValue == null || rightValue == null) {
                return AiDslTruth.UNKNOWN;
            }
            int comparison = leftValue.compareTo(rightValue);
            boolean result = switch (operator) {
                case LT -> comparison < 0;
                case LTE -> comparison <= 0;
                case GT -> comparison > 0;
                case GTE -> comparison >= 0;
                case EQ -> comparison == 0;
                case NEQ -> comparison != 0;
                default -> throw new IllegalStateException("Unexpected comparison operator: " + operator);
            };
            return result ? AiDslTruth.TRUE : AiDslTruth.FALSE;
        };
    }

    private AiDslNumericExpression numericExpression() {
        if (match(AiDslTokenType.NUMBER)) {
            BigDecimal value = new BigDecimal(previous().text());
            node();
            return evaluation -> value;
        }
        if (match(AiDslTokenType.OPEN, AiDslTokenType.HIGH, AiDslTokenType.LOW, AiDslTokenType.CLOSE,
                AiDslTokenType.VOLUME)) {
            AiDslField field = AiDslField.valueOf(previous().type().name());
            node();
            return evaluation -> evaluation.latest(field);
        }
        if (match(AiDslTokenType.SMA)) {
            expect(AiDslTokenType.LEFT_PAREN);
            AiDslField field = field();
            expect(AiDslTokenType.COMMA);
            int period = period();
            expect(AiDslTokenType.RIGHT_PAREN);
            node();
            return evaluation -> evaluation.sma(field, period);
        }
        if (match(AiDslTokenType.RSI)) {
            expect(AiDslTokenType.LEFT_PAREN);
            int period = period();
            expect(AiDslTokenType.RIGHT_PAREN);
            node();
            return evaluation -> evaluation.rsi(period);
        }
        if (match(AiDslTokenType.CHANGE_PCT)) {
            expect(AiDslTokenType.LEFT_PAREN);
            AiDslField field = field();
            expect(AiDslTokenType.COMMA);
            int period = period();
            expect(AiDslTokenType.RIGHT_PAREN);
            node();
            return evaluation -> evaluation.changePct(field, period);
        }
        throw error("Expected a number, candle field, or indicator function");
    }

    private AiDslField field() {
        if (!match(AiDslTokenType.OPEN, AiDslTokenType.HIGH, AiDslTokenType.LOW, AiDslTokenType.CLOSE,
                AiDslTokenType.VOLUME)) {
            throw error("Expected OPEN, HIGH, LOW, CLOSE, or VOLUME");
        }
        return AiDslField.valueOf(previous().type().name());
    }

    private int period() {
        expect(AiDslTokenType.NUMBER);
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

    private boolean match(AiDslTokenType... expected) {
        for (AiDslTokenType type : expected) {
            if (current().type() == type) {
                cursor++;
                return true;
            }
        }
        return false;
    }

    private void expect(AiDslTokenType expected) {
        if (!match(expected)) {
            throw error("Expected " + expected);
        }
    }

    private AiDslToken current() {
        return tokens.get(cursor);
    }

    private AiDslToken previous() {
        return tokens.get(cursor - 1);
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " near '" + current().text() + "'");
    }

    private static IllegalArgumentException syntax(String message, int offset) {
        return new IllegalArgumentException(message + " at offset " + offset);
    }
}
