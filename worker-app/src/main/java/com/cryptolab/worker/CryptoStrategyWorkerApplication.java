package com.cryptolab.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.cryptolab")
public class CryptoStrategyWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoStrategyWorkerApplication.class, args);
    }
}
