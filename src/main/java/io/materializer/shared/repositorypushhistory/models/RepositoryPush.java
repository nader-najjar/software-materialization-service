package io.materializer.shared.repositorypushhistory.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record RepositoryPush(

        @JsonProperty("id")
        @NotNull
        @Positive
        Long pushID,

        @JsonProperty("before")
        @NotNull
        @Pattern(regexp = "[0-9a-f]{40}")
        String before,

        @JsonProperty("after")
        @NotNull
        @Pattern(regexp = "[0-9a-f]{40}")
        String after,

        @JsonProperty("timestamp")
        @NotNull
        String pushedAt

) {
    public boolean deletesBranch() {
        return "0".repeat(40).equals(after);
    }
}
