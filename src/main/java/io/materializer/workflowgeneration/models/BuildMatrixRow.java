package io.materializer.workflowgeneration.models;

public record BuildMatrixRow(
        String repositoryName,
        String commitSHA,
        String buildSystem,
        String platform,
        String runner
) { }
