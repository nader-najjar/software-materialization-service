package io.materializer.execution;

import java.util.List;

import io.materializer.environment.EnvironmentVariables;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.publisherconfiguration.PublisherConfigurator;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutorRegistrationTest {

    private final MaterializerRegistryStore registryStore = mock(MaterializerRegistryStore.class);
    private final PublisherConfigurator publisherConfigurator = mock(PublisherConfigurator.class);
    private final LivePipelineInputsAdvancer advancer = mock(LivePipelineInputsAdvancer.class);
    private final LivePipelineInputsReconciler reconciler = mock(LivePipelineInputsReconciler.class);
    private final WorkflowGenerator generator = mock(WorkflowGenerator.class);
    private final WorkflowPublisher publisher = mock(WorkflowPublisher.class);
    private final Executor executor = executorWithRegistry("owner/registry");
    private final String[] arguments = {"{\"operation\":\"register\",\"repositoryName\":\"owner/materializer\"}"};

    @Test
    void delegatesRegistrationWithoutMaterializingOrRequiringLiveInputsStorage() {
        prepareRegistry();
        executor.execute(arguments);

        verify(publisherConfigurator).configure("owner/materializer");
        verifyNoInteractions(advancer, reconciler, generator, publisher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid"})
    void rejectsInvalidRegistryConfigurationBeforeRegistration(String repository) {
        Executor invalid = executorWithRegistry(repository);

        assertThrows(ValidationException.class, () -> invalid.execute(arguments));

        verifyNoInteractions(publisherConfigurator, registryStore, advancer, reconciler, generator, publisher);
    }

    @Test
    void propagatesRegistrationFailures() {
        prepareRegistry();
        IllegalArgumentException failure = new IllegalArgumentException("Already registered");
        doThrow(failure).when(publisherConfigurator).configure("owner/materializer");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(arguments)
        );

        assertSame(failure, exception);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingOrBlankPublisherConfigurationBeforeRegistration(String value) {
        Executor missingClientID = executorWithEnvironment(
                new EnvironmentVariables("token", "owner/registry", null, value, "example-private-key")
        );
        Executor missingPrivateKey = executorWithEnvironment(
                new EnvironmentVariables("token", "owner/registry", null, "example-client-id", value)
        );

        assertThrows(ValidationException.class, () -> missingClientID.execute(arguments));
        assertThrows(ValidationException.class, () -> missingPrivateKey.execute(arguments));

        verifyNoInteractions(publisherConfigurator, registryStore, advancer, reconciler, generator, publisher);
    }

    @Test
    void validatesTheRepositoryInputBeforeRegistration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(new String[] {"{\"operation\":\"register\"}"})
        );

        verifyNoInteractions(publisherConfigurator, registryStore, advancer, reconciler, generator, publisher);
    }

    private void prepareRegistry() {
        when(registryStore.readSnapshot()).thenReturn(
                new MaterializerRegistrySnapshot(new MaterializerRegistry(List.of()), "file-sha")
        );
    }

    private Executor executorWithRegistry(String repository) {
        return executorWithEnvironment(
                new EnvironmentVariables("token", repository, null, "example-client-id", "example-private-key")
        );
    }

    private Executor executorWithEnvironment(EnvironmentVariables environment) {
        return new Executor(
                new RegisterExecutor(
                        environment,
                        registryStore,
                        publisherConfigurator
                ),
                new MaterializeNewPushesExecutor(
                        advancer,
                        reconciler,
                        generator,
                        publisher,
                        environment,
                        registryStore
                )
        );
    }

}
