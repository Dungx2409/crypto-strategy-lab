package com.cryptolab.infrastructure.experiment.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class JdbcMarketDatasetRepositoryWiringTest {

    @Test
    void transactionalRepositoryRemainsProxyableBySpring() throws NoSuchMethodException {
        assertThat(Modifier.isFinal(JdbcMarketDatasetRepository.class.getModifiers())).isFalse();
        assertThat(JdbcMarketDatasetRepository.class
                        .getMethod("save", com.cryptolab.experiment.domain.MarketDataset.class, java.time.Instant.class)
                        .isAnnotationPresent(Transactional.class))
                .isTrue();
    }
}
