package com.cryptolab.experiment.domain;

public record JobDispatchMetadata(
        String evaluatorVersion,
        String codeCommit,
        String buildVersion) {

    public JobDispatchMetadata {
        evaluatorVersion = requireText(evaluatorVersion, "evaluatorVersion");
        codeCommit = requireText(codeCommit, "codeCommit");
        buildVersion = requireText(buildVersion, "buildVersion");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
