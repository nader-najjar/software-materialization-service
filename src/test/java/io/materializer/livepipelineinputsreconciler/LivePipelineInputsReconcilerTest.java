package io.materializer.livepipelineinputsreconciler;

import java.util.List;

import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.RepositoryPushHistory;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LivePipelineInputsReconcilerTest {

    private final LivePipelineDeclarationFetcher livePipelineDeclarationFetcher =
            mock(LivePipelineDeclarationFetcher.class);
    private final RepositoryPushHistory repositoryPushHistory = mock(RepositoryPushHistory.class);
    private final LivePipelineInputsStore livePipelineInputsStore = mock(LivePipelineInputsStore.class);
    private final LivePipelineInputsReconciler reconciler = new LivePipelineInputsReconciler(
            livePipelineDeclarationFetcher,
            repositoryPushHistory,
            livePipelineInputsStore
    );
    private final RepositorySnapshot materializerSnapshot = new RepositorySnapshot(
            "owner/materializer",
            "main",
            "a".repeat(40),
            42L
    );
    private final LivePipelineInputs inputs = new LivePipelineInputs(materializerSnapshot, List.of());

    @Test
    void fetchesAndValidatesThePinnedMaterializerDeclaration() {
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(declaredInput("owner/new", "main")),
                List.of(new DeclaredPipelineStep("Build", "build"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);

        RepositorySnapshot newSnapshot = new RepositorySnapshot("owner/new", "main", "b".repeat(40), 43L);
        when(repositoryPushHistory.latest("owner/new", "main")).thenReturn(newSnapshot);

        LivePipelineInputs result = reconciler.reconcile(inputs);

        verify(livePipelineInputsStore).write(result);

        assertEquals(new LivePipelineInputs(materializerSnapshot, List.of(newSnapshot)), result);
        verify(repositoryPushHistory).latest("owner/new", "main");
        verifyNoMoreInteractions(repositoryPushHistory);
        verify(livePipelineDeclarationFetcher).fetchAndValidate(materializerSnapshot);
        verifyNoMoreInteractions(livePipelineDeclarationFetcher);
    }

    @Test
    void propagatesInvalidDeclarations() {
        IllegalArgumentException failure = new IllegalArgumentException("Invalid pipeline declaration");
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenThrow(failure);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reconciler.reconcile(inputs)
        );

        assertSame(failure, exception);
        verifyNoInteractions(repositoryPushHistory, livePipelineInputsStore);
    }

    @Test
    void retainsMatchingPinsAndFetchesOnlyNewRepositoryBranches() {
        RepositorySnapshot first = new RepositorySnapshot("owner/first", "main", "b".repeat(40), 10L);
        RepositorySnapshot second = new RepositorySnapshot("owner/second", "release", "c".repeat(40), 11L);
        RepositorySnapshot removed = new RepositorySnapshot("owner/removed", "main", "d".repeat(40), 12L);
        prepareChangedDeclaration();
        List<RepositorySnapshot> previousSnapshots = List.of(first, second, removed);
        LivePipelineInputs previousInputs = new LivePipelineInputs(materializerSnapshot, previousSnapshots);

        RepositorySnapshot newBranch = new RepositorySnapshot("owner/first", "Main", "e".repeat(40), 13L);
        RepositorySnapshot newRepository = new RepositorySnapshot("owner/new", "main", "f".repeat(40), 14L);
        when(repositoryPushHistory.latest("owner/first", "Main")).thenReturn(newBranch);
        when(repositoryPushHistory.latest("owner/new", "main")).thenReturn(newRepository);

        LivePipelineInputs result = reconciler.reconcile(previousInputs);
        List<RepositorySnapshot> retained = result.declaredRepositorySnapshots();
        verify(livePipelineInputsStore).write(result);

        assertSame(materializerSnapshot, result.materializerRepositorySnapshot());
        assertEquals(List.of(second, first, newBranch, newRepository), retained);
        assertSame(second, retained.get(0));
        assertSame(first, retained.get(1));
        verify(repositoryPushHistory).latest("owner/first", "Main");
        verify(repositoryPushHistory).latest("owner/new", "main");
        verifyNoMoreInteractions(repositoryPushHistory);
    }

    @Test
    void doesNotSaveInvalidReconciledInputs() {
        RepositorySnapshot invalidSnapshot = new RepositorySnapshot("owner/new", "main", "invalid", 43L);
        prepareNewRepository(invalidSnapshot);

        assertThrows(ValidationException.class, () -> reconciler.reconcile(inputs));

        verifyNoInteractions(livePipelineInputsStore);
    }

    @Test
    void propagatesSaveFailures() {
        RepositorySnapshot snapshot = new RepositorySnapshot("owner/new", "main", "b".repeat(40), 43L);
        prepareNewRepository(snapshot);
        LivePipelineInputs expected = new LivePipelineInputs(materializerSnapshot, List.of(snapshot));
        IllegalStateException failure = new IllegalStateException("Could not save inputs");
        doThrow(failure).when(livePipelineInputsStore).write(expected);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reconciler.reconcile(inputs)
        );

        assertSame(failure, exception);
    }

    private void prepareChangedDeclaration() {
        List<DeclaredPipelineInput> declarations = List.of(
                declaredInput("OWNER/SECOND", "release"),
                declaredInput("owner/first", "main"),
                declaredInput("owner/first", "Main"),
                declaredInput("owner/new", "main")
        );

        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                declarations,
                List.of(new DeclaredPipelineStep("Build", "build"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);
    }

    private void prepareNewRepository(RepositorySnapshot snapshot) {
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(declaredInput("owner/new", "main")),
                List.of(new DeclaredPipelineStep("Build", "build"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);
        when(repositoryPushHistory.latest("owner/new", "main")).thenReturn(snapshot);
    }

    private DeclaredPipelineInput declaredInput(String repositoryName, String branch) {
        return new DeclaredPipelineInput(repositoryName, branch, "universal", List.of("x86_64-linux"));
    }

}
