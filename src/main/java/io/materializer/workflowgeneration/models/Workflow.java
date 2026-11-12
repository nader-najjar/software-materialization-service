package io.materializer.workflowgeneration.models;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Workflow(
        String name,

        @JsonProperty("run-name")
        String runName,

        Trigger on,

        Concurrency concurrency,

        Map<String, String> permissions,

        Map<String, Object> jobs
) {

    public Workflow {
        permissions = Map.copyOf(permissions);
        jobs = Collections.unmodifiableMap(new LinkedHashMap<>(jobs));
    }

    public record Trigger(
            Push push
    ) { }

    public record Concurrency(
            String group,

            String queue,

            @JsonProperty("cancel-in-progress")
            Boolean cancelInProgress
    ) { }

    public record Push(
            List<String> branches
    ) {

        public Push {
            branches = List.copyOf(branches);
        }
    }
}
