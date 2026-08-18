package com.cryptolab.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class CryptoStrategyApiApplicationTest {

    @Test
    void declaresAnIndependentSpringBootApplication() {
        assertThat(CryptoStrategyApiApplication.class)
                .hasAnnotation(SpringBootApplication.class);
    }
}
