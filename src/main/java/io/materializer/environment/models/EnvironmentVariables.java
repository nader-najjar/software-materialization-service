package io.materializer.environment.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EnvironmentVariables(

        @NotBlank
        String githubToken,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String materializerRegistryRepository,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String livePipelineInputsStoreRepository
) {
    @Override
    public String toString() {
        return "EnvironmentVariables[githubToken=REDACTED]";
    }
}
