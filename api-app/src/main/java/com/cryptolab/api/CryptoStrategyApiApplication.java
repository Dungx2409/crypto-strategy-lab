package com.cryptolab.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.cryptolab")
public class CryptoStrategyApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoStrategyApiApplication.class, args);
    }
}
