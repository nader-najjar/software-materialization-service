package io.materializer.pipelinestructureplanner.models;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RepositoryPush(
        @JsonProperty("id")
        long pushID,

        @JsonProperty("before")
        String before,

        @JsonProperty("after")
        String after,

        @JsonProperty("timestamp")
        Instant pushedAt
) {
    public boolean deletesBranch() {
        return "0".repeat(40).equals(after);
    }
}
