package com.cryptolab.news.domain;

public record ModelDescriptor(String name, String version) {

    public ModelDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        name = name.trim();
        version = version.trim();
    }
}
