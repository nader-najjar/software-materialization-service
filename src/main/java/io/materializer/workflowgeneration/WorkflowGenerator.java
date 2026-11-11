package io.materializer.workflowgeneration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import io.materializer.shared.PipelineDeclarationFetcher;
import io.materializer.shared.models.DeclaredPipelineInput;
import io.materializer.shared.models.DeclaredPipelineStep;
import io.materializer.shared.models.PipelineDeclaration;
import io.materializer.shared.models.PipelineStructure;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.YamlMapperUtility;
import io.materializer.workflowgeneration.models.Workflow;

public class WorkflowGenerator {

    private final PipelineDeclarationFetcher pipelineDeclarationFetcher;
    private final BuildJobGenerator buildJobGenerator;

    @Inject
    public WorkflowGenerator(
            PipelineDeclarationFetcher pipelineDeclarationFetcher,
            BuildJobGenerator buildJobGenerator
    ) {
        this.pipelineDeclarationFetcher = pipelineDeclarationFetcher;
        this.buildJobGenerator = buildJobGenerator;
    }

    public String generate(PipelineStructure structure) {
        Workflow workflow = generateWorkflow(structure);
        try {
            return YamlMapperUtility.MAPPER.writeValueAsString(workflow);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize materialization workflow", exception);
        }
    }

    Workflow generateWorkflow(PipelineStructure structure) {
        PipelineDeclaration pipelineDeclaration = pipelineDeclarationFetcher.fetch(
                structure.materializerRepositorySnapshot()
        );

        Map<String, Object> jobs = new LinkedHashMap<>();
        for (int index = 0; index < pipelineDeclaration.steps().size(); index++) {
            DeclaredPipelineStep step = pipelineDeclaration.steps().get(index);

            Object job = generateJob(
                    step,
                    pipelineDeclaration.repositories(),
                    structure.sourceSnapshots()
            );

            String jobID = "step_" + (index + 1);
            jobs.put(jobID, job);
        }

        Workflow.Push push = new Workflow.Push(List.of("execution"));
        Workflow.Trigger trigger = new Workflow.Trigger(push);
        Workflow.Concurrency concurrency = new Workflow.Concurrency("materialize", "single", false);
        Map<String, String> permissions = Map.of("contents", "read");
        return new Workflow("Materialize", "Materialize", trigger, concurrency, permissions, jobs);
    }

    private Object generateJob(
            DeclaredPipelineStep step,
            List<DeclaredPipelineInput> repositories,
            List<RepositorySnapshot> sourceSnapshots
    ) {
        return switch (step.operation()) {
            case "build" -> buildJobGenerator.generate(step, repositories, sourceSnapshots);
            default -> throw new IllegalArgumentException("Unsupported pipeline operation: " + step.operation());
        };
    }

}
