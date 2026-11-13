package io.materializer.execution;

import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.EnvironmentVariables;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaterializeNewPushesExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterializeNewPushesExecutor.class);

    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer;
    private final LivePipelineInputsReconciler livePipelineInputsReconciler;
    private final WorkflowGenerator workflowGenerator;
    private final WorkflowPublisher workflowPublisher;
    private final EnvironmentVariables environmentVariables;
    private final MaterializerRegistryStore registryStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the injected dependencies")
    @Inject
    public MaterializeNewPushesExecutor(
            LivePipelineInputsAdvancer livePipelineInputsAdvancer,
            LivePipelineInputsReconciler livePipelineInputsReconciler,
            WorkflowGenerator workflowGenerator,
            WorkflowPublisher workflowPublisher,
            EnvironmentVariables environmentVariables,
            MaterializerRegistryStore registryStore
    ) {
        this.livePipelineInputsAdvancer = livePipelineInputsAdvancer;
        this.livePipelineInputsReconciler = livePipelineInputsReconciler;
        this.workflowGenerator = workflowGenerator;
        this.workflowPublisher = workflowPublisher;
        this.environmentVariables = environmentVariables;
        this.registryStore = registryStore;
    }

    public void execute() {
        HibernateValidatorUtility.validateProperties(
                environmentVariables,
                "materializerRegistryRepository",
                "livePipelineInputsStoreRepository"
        );

        MaterializerRegistry materializerRegistry = registryStore.read();
        for (MaterializerRegistration registration : materializerRegistry.registrations()) {
            materializeNewPushForRegistration(registration);
        }
    }

    private void materializeNewPushForRegistration(MaterializerRegistration registration) {
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
