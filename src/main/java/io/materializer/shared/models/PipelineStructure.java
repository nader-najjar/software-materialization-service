package io.materializer.shared.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.models.annotations.UniqueSourceSnapshotsByRepositoryAndBranch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PipelineStructure(

        @JsonProperty("materializerRepositorySnapshot")
        @NotNull
        @Valid
        RepositorySnapshot materializerRepositorySnapshot,

        @JsonProperty("serviceRepositorySnapshot")
        @NotNull
        @Valid
        RepositorySnapshot serviceRepositorySnapshot,

        @JsonProperty("sourceSnapshots")
        @NotNull
        @UniqueSourceSnapshotsByRepositoryAndBranch
        List<@NotNull @Valid RepositorySnapshot> sourceSnapshots

) {
    public PipelineStructure {
        sourceSnapshots = sourceSnapshots == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(sourceSnapshots));
    }
}
