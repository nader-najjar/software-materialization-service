package io.materializer.livepipelineinputsadvancer.models;

import io.materializer.shared.repositorypushhistory.models.RepositoryPush;

public record PushCandidate(
    String repositoryName,
    String branch,
    RepositoryPush push
) { }
