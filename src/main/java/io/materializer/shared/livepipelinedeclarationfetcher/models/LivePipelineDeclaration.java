package io.materializer.shared.livepipelinedeclarationfetcher.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.livepipelinedeclarationfetcher.models.annotations.SingleLeadingBuildStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.annotations.UniquePipelineInputsByRepositoryAndBranch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record LivePipelineDeclaration(

        @JsonProperty("repositories")
        @NotEmpty
        @UniquePipelineInputsByRepositoryAndBranch
        List<@NotNull @Valid DeclaredPipelineInput> repositories,

        @JsonProperty("steps")
        @NotNull
        @SingleLeadingBuildStep
        List<@NotNull @Valid DeclaredPipelineStep> steps

) {
    public LivePipelineDeclaration {
        repositories = repositories == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(repositories));
        steps = steps == null ? null : Collections.unmodifiableList(new ArrayList<>(steps));
    }
}
