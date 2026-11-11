package io.materializer.execution;

import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.execution.models.ApplicationInput;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.materializerregistryfetcher.MaterializerRegistryFetcher;
import io.materializer.materializerregistryfetcher.models.MaterializerRegistration;
import io.materializer.materializerregistryfetcher.models.MaterializerRegistry;
import io.materializer.shared.models.LivePipelineInputs;
import io.materializer.shared.utilities.JsonSanitizer;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(Executor.class);

    private final MaterializerRegistryFetcher materializerRegistryFetcher;
    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer;
    private final LivePipelineInputsReconciler livePipelineInputsReconciler;
    private final WorkflowGenerator workflowGenerator;
    private final WorkflowPublisher workflowPublisher;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared workflow publisher")
    @Inject
    public Executor(
            MaterializerRegistryFetcher materializerRegistryFetcher,
            LivePipelineInputsAdvancer livePipelineInputsAdvancer,
            LivePipelineInputsReconciler livePipelineInputsReconciler,
            WorkflowGenerator workflowGenerator,
            WorkflowPublisher workflowPublisher
    ) {
        this.materializerRegistryFetcher = materializerRegistryFetcher;
        this.livePipelineInputsAdvancer = livePipelineInputsAdvancer;
        this.livePipelineInputsReconciler = livePipelineInputsReconciler;
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
        MaterializerRegistry materializerRegistry = materializerRegistryFetcher.fetch();
        for (MaterializerRegistration registration : materializerRegistry.registrations()) {
            materializeNextRegistrationUpdate(registration);
        }
    }

    private void materializeNextRegistrationUpdate(MaterializerRegistration registration) {
        try {
            Optional<LivePipelineInputs> inputs = livePipelineInputsAdvancer.advance(registration.repositoryName());
            if (inputs.isEmpty()) { return; }

            LivePipelineInputs reconciledInputs = livePipelineInputsReconciler.reconcile(inputs.get());

            String workflowYaml = workflowGenerator.generate(reconciledInputs);

            String materializerRepositoryName = reconciledInputs.materializerRepositorySnapshot().repositoryName();
            workflowPublisher.publish(materializerRepositoryName, workflowYaml);

        } catch (RuntimeException exception) {
            LOGGER.error("Could not materialize {}", registration.repositoryName(), exception);
        }
    }

}
