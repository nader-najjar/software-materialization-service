package io.materializer.shared.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.models.annotations.UniqueDeclaredRepositorySnapshotsByRepositoryAndBranch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record LivePipelineInputs(

        @JsonProperty("materializerRepositorySnapshot")
        @NotNull
        @Valid
        RepositorySnapshot materializerRepositorySnapshot,

        @JsonProperty("declaredRepositorySnapshots")
        @NotNull
        @UniqueDeclaredRepositorySnapshotsByRepositoryAndBranch
        List<@NotNull @Valid RepositorySnapshot> declaredRepositorySnapshots

) {
    public LivePipelineInputs {
        declaredRepositorySnapshots = declaredRepositorySnapshots == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(declaredRepositorySnapshots));
    }
}
