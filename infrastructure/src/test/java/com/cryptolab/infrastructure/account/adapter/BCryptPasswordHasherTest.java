package com.cryptolab.infrastructure.account.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    @Test
    void storesAOneWayHashAndChecksPasswords() {
        BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        String hash = hasher.hash("password123");

        assertThat(hash).startsWith("$2").isNotEqualTo("password123");
        assertThat(hasher.matches("password123", hash)).isTrue();
        assertThat(hasher.matches("different123", hash)).isFalse();
    }
}
