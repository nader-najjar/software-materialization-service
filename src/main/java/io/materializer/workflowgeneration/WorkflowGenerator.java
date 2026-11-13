package io.materializer.workflowgeneration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.YamlMapperUtility;
import io.materializer.workflowgeneration.models.Workflow;

public class WorkflowGenerator {

    private final LivePipelineDeclarationFetcher livePipelineDeclarationFetcher;
    private final BuildJobGenerator buildJobGenerator;

    @Inject
    public WorkflowGenerator(
            LivePipelineDeclarationFetcher livePipelineDeclarationFetcher,
            BuildJobGenerator buildJobGenerator
    ) {
        this.livePipelineDeclarationFetcher = livePipelineDeclarationFetcher;
        this.buildJobGenerator = buildJobGenerator;
    }

    public String generate(LivePipelineInputs inputs) {
        Workflow workflow = generateWorkflow(inputs);
        try {
            return YamlMapperUtility.MAPPER.writeValueAsString(workflow);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize materialization workflow", exception);
        }
    }

    Workflow generateWorkflow(LivePipelineInputs inputs) {
        LivePipelineDeclaration livePipelineDeclaration = livePipelineDeclarationFetcher.fetchAndValidate(
                inputs.materializerRepositorySnapshot()
        );

        Map<String, Object> jobs = new LinkedHashMap<>();
        for (int index = 0; index < livePipelineDeclaration.steps().size(); index++) {
            DeclaredPipelineStep step = livePipelineDeclaration.steps().get(index);

            Object job = generateJob(
                    step,
                    livePipelineDeclaration.repositories(),
                    inputs.declaredRepositorySnapshots()
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
            List<RepositorySnapshot> declaredRepositorySnapshots
    ) {
        return switch (step.operation()) {
            case "build" -> buildJobGenerator.generate(step, repositories, declaredRepositorySnapshots);
            default -> throw new IllegalArgumentException("Unsupported pipeline operation: " + step.operation());
        };
    }

}
