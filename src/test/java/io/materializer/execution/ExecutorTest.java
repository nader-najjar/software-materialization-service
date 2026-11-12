package io.materializer.execution;

import java.util.List;
import java.util.Optional;

import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.materializerregistryfetcher.MaterializerRegistryFetcher;
import io.materializer.materializerregistryfetcher.models.MaterializerRegistration;
import io.materializer.materializerregistryfetcher.models.MaterializerRegistry;
import io.materializer.shared.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutorTest {

    private final MaterializerRegistryFetcher registryFetcher = mock(MaterializerRegistryFetcher.class);
    private final LivePipelineInputsAdvancer advancer = mock(LivePipelineInputsAdvancer.class);
    private final LivePipelineInputsReconciler reconciler = mock(LivePipelineInputsReconciler.class);
    private final WorkflowGenerator generator = mock(WorkflowGenerator.class);
    private final WorkflowPublisher publisher = mock(WorkflowPublisher.class);
    private final Executor executor = new Executor(registryFetcher, advancer, reconciler, generator, publisher);
    private final MaterializerRegistration registration = new MaterializerRegistration("owner/materializer");

    @Test
    void doesNothingForAnEmptyRegistry() {
        when(registryFetcher.fetch()).thenReturn(new MaterializerRegistry(List.of()));

        execute();

        verifyNoInteractions(advancer, reconciler, generator, publisher);
    }

    @Test
    void doesNothingWhenRegisteredPipelineHasNoUpdate() {
        when(registryFetcher.fetch()).thenReturn(new MaterializerRegistry(List.of(registration)));
        when(advancer.advance(registration.repositoryName())).thenReturn(Optional.empty());

        execute();

        verifyNoInteractions(reconciler, generator, publisher);
    }

    @Test
    void propagatesRegistryFailures() {
        when(registryFetcher.fetch()).thenThrow(new IllegalArgumentException("Invalid registry"));

        assertThrows(IllegalArgumentException.class, this::execute);

        verifyNoInteractions(advancer, reconciler, generator, publisher);
    }

    @Test
    void generatesAndPublishesFromReconciledInputs() {
        when(registryFetcher.fetch()).thenReturn(new MaterializerRegistry(List.of(registration)));
        LivePipelineInputs advanced = inputs(registration.repositoryName());
        LivePipelineInputs reconciled = prepareSuccessfulRegistration(registration);

        execute();

        InOrder order = inOrder(advancer, reconciler, generator, publisher);
        order.verify(advancer).advance(registration.repositoryName());
        order.verify(reconciler).reconcile(advanced);
        order.verify(generator).generate(reconciled);
        order.verify(publisher).publish(registration.repositoryName(), "workflow");
    }

    @ParameterizedTest
    @ValueSource(strings = {"advance", "reconcile", "generate", "publish"})
    void continuesToNextRegistrationAfterFailure(String stage) {
        MaterializerRegistration broken = new MaterializerRegistration("owner/broken");
        when(registryFetcher.fetch()).thenReturn(new MaterializerRegistry(List.of(broken, registration)));
        prepareFailure(broken, stage);
        LivePipelineInputs reconciled = prepareSuccessfulRegistration(registration);

        execute();

        InOrder order = inOrder(generator, publisher);
        order.verify(generator).generate(reconciled);
        order.verify(publisher).publish(registration.repositoryName(), "workflow");
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(null));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(new String[0]));
        verifyNoInteractions(registryFetcher, advancer);
    }

    @Test
    void rejectsUnknownOperations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(new String[] {"{\"operation\":\"unknown\"}"})
        );
        verifyNoInteractions(registryFetcher, advancer);
    }

    private void execute() {
        executor.execute(new String[] {"{\"operation\":\"materialize-next\"}"});
    }

    private LivePipelineInputs prepareSuccessfulRegistration(MaterializerRegistration materializerRegistration) {
        LivePipelineInputs advanced = inputs(materializerRegistration.repositoryName());
        RepositorySnapshot source = new RepositorySnapshot("owner/source", "main", "b".repeat(40), 2L);
        RepositorySnapshot materializerSnapshot = advanced.materializerRepositorySnapshot();
        LivePipelineInputs reconciled = new LivePipelineInputs(materializerSnapshot, List.of(source));
        when(advancer.advance(materializerRegistration.repositoryName())).thenReturn(Optional.of(advanced));
        when(reconciler.reconcile(advanced)).thenReturn(reconciled);
        when(generator.generate(reconciled)).thenReturn("workflow");
        return reconciled;
    }

    private void prepareFailure(MaterializerRegistration materializerRegistration, String stage) {
        LivePipelineInputs advanced = inputs(materializerRegistration.repositoryName());
        LivePipelineInputs reconciled = prepareSuccessfulRegistration(materializerRegistration);
        IllegalStateException failure = new IllegalStateException("Failed " + stage);
        switch (stage) {
            case "advance" -> when(advancer.advance(materializerRegistration.repositoryName())).thenThrow(failure);
            case "reconcile" -> when(reconciler.reconcile(advanced)).thenThrow(failure);
            case "generate" -> when(generator.generate(reconciled)).thenThrow(failure);
            case "publish" -> {
                doThrow(failure).when(publisher).publish(materializerRegistration.repositoryName(), "workflow");
            }
            default -> throw new IllegalArgumentException(stage);
        }
    }

    private LivePipelineInputs inputs(String repositoryName) {
        RepositorySnapshot snapshot = new RepositorySnapshot(repositoryName, "main", "a".repeat(40), 1L);
        return new LivePipelineInputs(snapshot, List.of());
    }
}
