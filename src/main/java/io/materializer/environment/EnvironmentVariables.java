package io.materializer.environment;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EnvironmentVariables(

        @JsonProperty(value = "GITHUB_TOKEN", access = JsonProperty.Access.WRITE_ONLY)
        @NotNull
        @NotBlank
        String githubToken,

        @JsonProperty("MATERIALIZER_REGISTRY_REPOSITORY")
        @NotNull
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String materializerRegistryRepository,

        @JsonProperty("LIVE_PIPELINE_INPUTS_STORE_REPOSITORY")
        @NotNull
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String livePipelineInputsStoreRepository,

        @JsonProperty("BUILD_PUBLISHER_APP_CLIENT_ID")
        @NotNull
        @NotBlank
        String buildPublisherAppClientID,

        @JsonProperty(value = "BUILD_PUBLISHER_APP_PRIVATE_KEY", access = JsonProperty.Access.WRITE_ONLY)
        @NotNull
        @NotBlank
        String buildPublisherAppPrivateKey
) {
    @Override
    public String toString() {
        return "EnvironmentVariables[REDACTED]";
    }
}
