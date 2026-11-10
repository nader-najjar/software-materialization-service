package io.materializer.shared.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record DeclaredPipelineStep(

        @JsonProperty("name")
        @NotBlank
        String name,

        @JsonProperty("operation")
        @NotBlank
        String operation

) { }
