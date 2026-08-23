package com.cryptolab.api.search;

import java.util.List;

public record SearchCapabilitiesResponse(
        String defaultGenerator,
        List<String> availableGenerators,
        String engineVersion,
        String fillPolicy) {}
