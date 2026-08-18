package com.cryptolab.infrastructure.marketdata.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JdbcCandleStoreWiringTest {

    @Test
    void marksTheProductionConstructorForDeterministicSpringWiring() {
        long autowiredConstructors = Arrays.stream(JdbcCandleStore.class.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertThat(autowiredConstructors).isEqualTo(1);
        assertThat(Modifier.isFinal(JdbcCandleStore.class.getModifiers())).isFalse();
    }
}
