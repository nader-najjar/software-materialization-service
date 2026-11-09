package io.materializer.pipelinestructureplanner.models;

public record PushCandidate(
    RepositoryRole role,
    String repositoryName,
    String branch,
    RepositoryPush push
) { }
