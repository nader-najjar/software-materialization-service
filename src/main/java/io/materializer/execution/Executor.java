package io.materializer.execution;

import com.google.inject.Inject;
import io.materializer.execution.models.ApplicationInput;
import io.materializer.pipelinestructureplanner.PipelineStructurePlanner;
import io.materializer.shared.models.PipelineStructure;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonSanitizer;

public class Executor {

    private final PipelineStructurePlanner pipelineStructurePlanner;

    @Inject
    public Executor(PipelineStructurePlanner pipelineStructurePlanner) {
        this.pipelineStructurePlanner = pipelineStructurePlanner;
    }

    public void execute(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("No input provided");
        }

        ApplicationInput input = JsonSanitizer.parseAndValidate(args[0], ApplicationInput.class);
        switch (input.operation()) {
            // Per repo, retry an undispatched structure first and skip planning for that pass.
            // Otherwise, save the next structure as current with dispatched=false, then dispatch it.
            // Set dispatched=true once GitHub accepts. A retry may bounce the same pins.
            // The target workflow runs one at a time per repo and throws before doing work if its structure is stale.
            // You can retry just one job in the GitHub UI, so check for stale pins at the start of every job.
            /*
             * Save new (throw if save fails) -> older runs can no longer start work -> dispatch new.
             * Dispatch fails -> retry next poll. Pipeline fails after dispatch -> rerun in GitHub if transient,
             * otherwise push a fix.
             */
            case "materialize-next" -> materializeNext();
            default -> throw new IllegalArgumentException("Unsupported operation: " + input.operation());
        }
    }

    private void materializeNext() {
        for (PipelineStructure structure : pipelineStructurePlanner.planNextStructures()) {
            HibernateValidatorUtility.validate(structure);
        }
    }
}
