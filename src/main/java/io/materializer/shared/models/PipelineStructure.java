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

        @JsonProperty("sourceSnapshots")
        @NotNull
        @UniqueSourceSnapshotsByRepositoryAndBranch
        List<@NotNull @Valid RepositorySnapshot> sourceSnapshots,

        @JsonProperty("dispatched")
        boolean dispatched

) {
    public PipelineStructure {
        sourceSnapshots = sourceSnapshots == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(sourceSnapshots));
    }

    public PipelineStructure withDispatchMarked() {
        return new PipelineStructure(materializerRepositorySnapshot, sourceSnapshots, true);
    }
}
