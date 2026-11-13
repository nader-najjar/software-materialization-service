package io.materializer.execution;

import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.registration.MaterializerRegistrar;
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

class ExecutorRegistrationTest {

    private final MaterializerRegistryStore registryStore = mock(MaterializerRegistryStore.class);
    private final LivePipelineInputsAdvancer advancer = mock(LivePipelineInputsAdvancer.class);
    private final LivePipelineInputsReconciler reconciler = mock(LivePipelineInputsReconciler.class);
    private final WorkflowGenerator generator = mock(WorkflowGenerator.class);
    private final WorkflowPublisher publisher = mock(WorkflowPublisher.class);
    private final MaterializerRegistrar registrar = mock(MaterializerRegistrar.class);
    private final Executor executor = executorWithRegistry("owner/registry");
    private final String[] arguments = {"{\"operation\":\"register\",\"repositoryName\":\"owner/materializer\"}"};

    @Test
    void delegatesRegistrationWithoutMaterializingOrRequiringLiveInputsStorage() {
        executor.execute(arguments);

        verify(registrar).register("owner/materializer");
        verifyNoInteractions(registryStore, advancer, reconciler, generator, publisher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid"})
    void rejectsInvalidRegistryConfigurationBeforeRegistration(String repository) {
        Executor invalid = executorWithRegistry(repository);

        assertThrows(ValidationException.class, () -> invalid.execute(arguments));

        verifyNoInteractions(registrar, registryStore, advancer, reconciler, generator, publisher);
    }

    @Test
    void propagatesRegistrationFailures() {
        IllegalArgumentException failure = new IllegalArgumentException("Already registered");
        doThrow(failure).when(registrar).register("owner/materializer");

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

        verifyNoInteractions(registrar, registryStore, advancer, reconciler, generator, publisher);
    }

    @Test
    void validatesTheRepositoryInputBeforeRegistration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(new String[] {"{\"operation\":\"register\"}"})
        );

        verifyNoInteractions(registrar, registryStore, advancer, reconciler, generator, publisher);
    }

    private Executor executorWithRegistry(String repository) {
        return executorWithEnvironment(
                new EnvironmentVariables("token", repository, null, "example-client-id", "example-private-key")
        );
    }

    private Executor executorWithEnvironment(EnvironmentVariables environment) {
        return new Executor(registryStore, advancer, reconciler, generator, publisher, environment, registrar);
    }

}
