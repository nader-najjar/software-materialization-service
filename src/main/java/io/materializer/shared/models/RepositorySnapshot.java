package io.materializer.shared.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record RepositorySnapshot(

        @JsonProperty("repositoryName")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String repositoryName,

        @JsonProperty("branch")
        @NotBlank
        String branch,

        @JsonProperty("commitSHA")
        @NotNull
        @Pattern(regexp = "[0-9a-f]{40}")
        String commitSHA,

        @JsonProperty("pushID")
        @NotNull
        @Positive
        Long pushID

) { }
