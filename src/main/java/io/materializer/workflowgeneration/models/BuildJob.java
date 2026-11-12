package io.materializer.workflowgeneration.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BuildJob(
        String name,

        @JsonProperty("runs-on")
        String runsOn,

        Strategy strategy,

        List<JobStep> steps
) {

    public BuildJob {
        steps = List.copyOf(steps);
    }

    public record Strategy(
            @JsonProperty("fail-fast")
            Boolean failFast,

            Matrix matrix
    ) { }

    public record Matrix(
            List<BuildMatrixRow> include
    ) {

        public Matrix {
            include = List.copyOf(include);
        }
    }
}
