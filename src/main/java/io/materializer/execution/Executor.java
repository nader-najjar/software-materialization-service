package io.materializer.execution;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.execution.models.ApplicationInput;
import io.materializer.pipelinestructureplanner.PipelineStructurePlanner;
import io.materializer.shared.PipelineStructureStore;
import io.materializer.shared.models.PipelineStructure;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonSanitizer;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;

public class Executor {

    private final PipelineStructurePlanner pipelineStructurePlanner;
    private final PipelineStructureStore pipelineStructureStore;
    private final WorkflowGenerator workflowGenerator;
    private final WorkflowPublisher workflowPublisher;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The injected store keeps no per-run state")
    @Inject
    public Executor(
            PipelineStructurePlanner pipelineStructurePlanner,
            PipelineStructureStore pipelineStructureStore,
            WorkflowGenerator workflowGenerator,
            WorkflowPublisher workflowPublisher
    ) {
        this.pipelineStructurePlanner = pipelineStructurePlanner;
        this.pipelineStructureStore = pipelineStructureStore;
        this.workflowGenerator = workflowGenerator;
        this.workflowPublisher = workflowPublisher;
    }

    public void execute(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("No input provided");
        }

        ApplicationInput input = JsonSanitizer.parseAndValidate(args[0], ApplicationInput.class);
        switch (input.operation()) {
            case "materialize-next" -> materializeNext();
            default -> throw new IllegalArgumentException("Unsupported operation: " + input.operation());
        }
    }

    private void materializeNext() {
        for (PipelineStructure structure : pipelineStructurePlanner.planNextStructures()) {
            HibernateValidatorUtility.validate(structure);
            pipelineStructureStore.write(structure);

            String workflowYaml = workflowGenerator.generate(structure);
            String materializerRepositoryName = structure.materializerRepositorySnapshot().repositoryName();
            workflowPublisher.publish(materializerRepositoryName, workflowYaml);

            PipelineStructure dispatchedStructure = structure.withDispatchMarked();
            pipelineStructureStore.write(dispatchedStructure);
        }
    }
}
