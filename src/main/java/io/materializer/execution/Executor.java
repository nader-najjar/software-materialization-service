package io.materializer.execution;

import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.execution.models.ApplicationInput;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.registration.MaterializerRegistrar;
import io.materializer.shared.models.LivePipelineInputs;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonSanitizer;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(Executor.class);

    private final MaterializerRegistryStore materializerRegistryStore;
    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer;
    private final LivePipelineInputsReconciler livePipelineInputsReconciler;
    private final WorkflowGenerator workflowGenerator;
    private final WorkflowPublisher workflowPublisher;
    private final EnvironmentVariables environmentVariables;
    private final MaterializerRegistrar materializerRegistrar;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared workflow publisher")
    @Inject
    public Executor(
            MaterializerRegistryStore materializerRegistryStore,
            LivePipelineInputsAdvancer livePipelineInputsAdvancer,
            LivePipelineInputsReconciler livePipelineInputsReconciler,
            WorkflowGenerator workflowGenerator,
            WorkflowPublisher workflowPublisher,
            EnvironmentVariables environmentVariables,
            MaterializerRegistrar materializerRegistrar
    ) {
        this.materializerRegistryStore = materializerRegistryStore;
        this.livePipelineInputsAdvancer = livePipelineInputsAdvancer;
        this.livePipelineInputsReconciler = livePipelineInputsReconciler;
        this.workflowGenerator = workflowGenerator;
        this.workflowPublisher = workflowPublisher;
        this.environmentVariables = environmentVariables;
        this.materializerRegistrar = materializerRegistrar;
    }

    public void execute(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("No input provided");
        }

        ApplicationInput input = JsonSanitizer.parseAndValidate(args[0], ApplicationInput.class);
        switch (input.operation()) {
            case "register" -> register(input.repositoryName());
            case "materialize-next" -> materializeNext();
            default -> throw new IllegalArgumentException("Unsupported operation: " + input.operation());
        }
    }

    private void register(String repositoryName) {
        HibernateValidatorUtility.validateProperties(
                environmentVariables,
                "materializerRegistryRepository",
                "buildPublisherAppClientID",
                "buildPublisherAppPrivateKey"
        );

        materializerRegistrar.register(repositoryName);
    }

    private void materializeNext() {
        HibernateValidatorUtility.validateProperties(
                environmentVariables,
                "materializerRegistryRepository",
                "livePipelineInputsStoreRepository"
        );

        MaterializerRegistry materializerRegistry = materializerRegistryStore.read();
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
