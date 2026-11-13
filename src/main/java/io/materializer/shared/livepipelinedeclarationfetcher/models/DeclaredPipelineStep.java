package io.materializer.shared.livepipelinedeclarationfetcher.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeclaredPipelineStep(

        @JsonProperty("name")
        @NotBlank
        String name,

        @JsonProperty("operation")
        @NotBlank
        @Pattern(regexp = "build", message = "Supported operation: build")
        String operation

) { }
