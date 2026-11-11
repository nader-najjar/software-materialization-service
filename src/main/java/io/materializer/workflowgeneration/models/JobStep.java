package io.materializer.workflowgeneration.models;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// We include this annotation because jobs can have very different
// steps, and making one pojo for each permutation would be impractical.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobStep(
        String name,

        String id,

        @JsonProperty("if")
        String condition,

        String uses,

        String run,

        Map<String, String> with,

        Map<String, String> env
) {

    public JobStep {
        with = with == null ? null : Map.copyOf(with);
        env = env == null ? null : Map.copyOf(env);
    }
}
