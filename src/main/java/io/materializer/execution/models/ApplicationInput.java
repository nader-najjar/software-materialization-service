package io.materializer.execution.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ApplicationInput(
        @JsonProperty("operation")
        @NotNull
        @NotBlank
        @Pattern(regexp = "materialize-new-pushes|register")
        String operation,

        @JsonProperty("repositoryName")
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String repositoryName
) {

    @AssertTrue(message = "repositoryName is required for register")
    public boolean isRepositoryNamePresentWhenRequired() {
        return !"register".equals(operation) || repositoryName != null;
    }

}
