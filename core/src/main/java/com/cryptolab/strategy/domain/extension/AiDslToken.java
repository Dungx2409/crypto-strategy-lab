package com.cryptolab.strategy.domain.extension;

import java.math.BigDecimal;

enum AiDslTokenType {
    BUY,
    SELL,
    WHEN,
    AND,
    OR,
    NOT,
    OPEN,
    HIGH,
    LOW,
    CLOSE,
    VOLUME,
    SMA,
    RSI,
    CHANGE_PCT,
    NUMBER,
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,
    LT,
    LTE,
    GT,
    GTE,
    EQ,
    NEQ,
    EOF
}

record AiDslToken(AiDslTokenType type, String text) {}

enum AiDslTruth {
    TRUE,
    FALSE,
    UNKNOWN;

    AiDslTruth not() {
        return switch (this) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case UNKNOWN -> UNKNOWN;
        };
    }

    AiDslTruth and(AiDslTruth other) {
        if (this == FALSE || other == FALSE) {
            return FALSE;
        }
        return this == UNKNOWN || other == UNKNOWN ? UNKNOWN : TRUE;
    }

    AiDslTruth or(AiDslTruth other) {
        if (this == TRUE || other == TRUE) {
            return TRUE;
        }
        return this == UNKNOWN || other == UNKNOWN ? UNKNOWN : FALSE;
    }
}

@FunctionalInterface
interface AiDslBooleanExpression {
    AiDslTruth evaluate(AiDslEvaluation evaluation);
}

@FunctionalInterface
interface AiDslNumericExpression {
    BigDecimal evaluate(AiDslEvaluation evaluation);
}
