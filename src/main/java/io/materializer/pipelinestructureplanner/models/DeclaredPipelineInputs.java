package io.materializer.pipelinestructureplanner.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.pipelinestructureplanner.models.annotations.UniquePipelineInputsByRepositoryAndBranch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DeclaredPipelineInputs(

        @JsonProperty("repositories")
        @NotNull
        @UniquePipelineInputsByRepositoryAndBranch
        List<@NotNull @Valid PipelineInput> repositories

) {
    public DeclaredPipelineInputs {
        repositories = repositories == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(repositories));
    }
}
