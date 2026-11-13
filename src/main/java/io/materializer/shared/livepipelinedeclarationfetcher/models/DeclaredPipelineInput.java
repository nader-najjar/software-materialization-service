package io.materializer.shared.livepipelinedeclarationfetcher.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record DeclaredPipelineInput(

        @JsonProperty("repositoryName")
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String repositoryName,

        @JsonProperty("branch")
        @NotBlank
        String branch,

        @JsonProperty("build_system")
        @NotBlank
        @Pattern(regexp = "universal", message = "Supported build system: universal")
        String buildSystem,

        @JsonProperty("platforms")
        @NotEmpty
        List<@NotBlank @Pattern(
                regexp = "aarch64-linux|x86_64-linux",
                message = "Supported platforms: aarch64-linux, x86_64-linux"
        ) String> platforms

) {
    public DeclaredPipelineInput {
        platforms = platforms == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(platforms));
    }
}
