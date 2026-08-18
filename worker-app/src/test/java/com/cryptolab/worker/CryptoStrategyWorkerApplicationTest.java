package com.cryptolab.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class CryptoStrategyWorkerApplicationTest {

    @Test
    void declaresAnIndependentSpringBootApplication() {
        assertThat(CryptoStrategyWorkerApplication.class)
                .hasAnnotation(SpringBootApplication.class);
    }
}
