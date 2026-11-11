package io.materializer.livepipelineinputsadvancer.models;

import io.materializer.shared.models.RepositoryPush;

public record PushCandidate(
    String repositoryName,
    String branch,
    RepositoryPush push
) { }
