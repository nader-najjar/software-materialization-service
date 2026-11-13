package io.materializer.livepipelineinputsadvancer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.materializer.livepipelineinputsadvancer.models.PushCandidate;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.RepositoryPushHistory;
import io.materializer.shared.repositorypushhistory.models.RepositoryPush;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LivePipelineInputsAdvancerTest {

    private final RepositoryPushHistory repositoryPushHistory = mock(RepositoryPushHistory.class);
    private final LivePipelineInputsStore store = mock(LivePipelineInputsStore.class);
    private final LivePipelineInputsAdvancer advancer = new LivePipelineInputsAdvancer(repositoryPushHistory, store);

    @Test
    void initializesOnlyTheMaterializerPin() {
        RepositorySnapshot pin = new RepositorySnapshot("owner/mat", "trunk", "a".repeat(40), 12L);
        when(repositoryPushHistory.latestOnDefaultBranch("owner/mat")).thenReturn(pin);

        Optional<LivePipelineInputs> result = advancer.advance("owner/mat");

        verify(store).read("owner/mat");
        verify(repositoryPushHistory).latestOnDefaultBranch("owner/mat");
        assertEquals(Optional.of(new LivePipelineInputs(pin, List.of())), result);
        verify(store).write(result.orElseThrow());
        verifyNoMoreInteractions(repositoryPushHistory, store);
    }

    @Test
    void doesNotSaveInvalidInputs() {
        when(repositoryPushHistory.latestOnDefaultBranch("owner/mat"))
                .thenReturn(new RepositorySnapshot("owner/mat", "main", "invalid", 12L));

        assertThrows(ValidationException.class, () -> advancer.advance("owner/mat"));
        verify(store).read("owner/mat");
        verifyNoMoreInteractions(store);
    }

    @Test
    void usesTheExistingInputsBranchWithoutInitializingAgain() {
        RepositorySnapshot pin = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 12L);
        when(store.read("owner/mat")).thenReturn(Optional.of(new LivePipelineInputs(pin, List.of())));

        Optional<LivePipelineInputs> result = advancer.advance("owner/mat");

        verify(store).read("owner/mat");
        assertEquals(Optional.empty(), result);
        verify(repositoryPushHistory).since(pin);
        verifyNoMoreInteractions(store, repositoryPushHistory);
    }

    @Test
    void selectsTheOldestPushAcrossAllTrackedRepositories() {
        RepositorySnapshot mat = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);
        RepositorySnapshot first = new RepositorySnapshot("owner/first", "main", "a".repeat(40), 2L);
        RepositorySnapshot second = new RepositorySnapshot("owner/second", "release", "a".repeat(40), 3L);
        RepositoryPush matPush = push(10, 30);
        RepositoryPush firstPush = push(11, 20);
        RepositoryPush oldest = push(12, 10);
        when(repositoryPushHistory.since(mat)).thenReturn(List.of(matPush));
        when(repositoryPushHistory.since(first)).thenReturn(List.of(firstPush));
        when(repositoryPushHistory.since(second)).thenReturn(List.of(push(13, 40), oldest));

        LivePipelineInputs inputs = new LivePipelineInputs(mat, List.of(first, second));
        Optional<PushCandidate> selected = advancer.findOldestPushCandidate(inputs);

        PushCandidate expected = new PushCandidate("owner/second", "release", oldest);
        assertEquals(Optional.of(expected), selected);
        verifyNoMoreInteractions(store);
    }

    @Test
    void breaksTimestampTiesByPushID() {
        RepositorySnapshot mat = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);
        RepositorySnapshot source = new RepositorySnapshot("owner/source", "main", "a".repeat(40), 2L);
        RepositoryPush oldest = push(10, 10);
        when(repositoryPushHistory.since(mat)).thenReturn(List.of(oldest));
        when(repositoryPushHistory.since(source)).thenReturn(List.of(push(11, 10)));

        LivePipelineInputs inputs = new LivePipelineInputs(mat, List.of(source));
        Optional<PushCandidate> selected = advancer.findOldestPushCandidate(inputs);

        PushCandidate expected = new PushCandidate("owner/mat", "main", oldest);
        assertEquals(Optional.of(expected), selected);
    }

    @Test
    void returnsEmptyWhenNoPushesAreAvailable() {
        RepositorySnapshot mat = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);

        assertEquals(Optional.empty(), advancer.findOldestPushCandidate(new LivePipelineInputs(mat, List.of())));
        verifyNoMoreInteractions(store);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void savesOnlyTheSelectedRepositoryUpdate(boolean updateMaterializer) {
        RepositorySnapshot mat = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);
        RepositorySnapshot source = new RepositorySnapshot("owner/source", "release", "a".repeat(40), 2L);
        LivePipelineInputs inputs = new LivePipelineInputs(mat, List.of(source));
        RepositorySnapshot selected = updateMaterializer ? mat : source;
        when(store.read("owner/mat")).thenReturn(Optional.of(inputs));
        when(repositoryPushHistory.since(selected)).thenReturn(List.of(push(10, 10), push(11, 20)));
        RepositorySnapshot updated = new RepositorySnapshot(
                selected.repositoryName(),
                selected.branch(),
                "b".repeat(40),
                10L
        );
        LivePipelineInputs expected = updateMaterializer
                ? new LivePipelineInputs(updated, List.of(source))
                : new LivePipelineInputs(mat, List.of(updated));

        Optional<LivePipelineInputs> result = advancer.advance("owner/mat");

        verify(store).read("owner/mat");
        assertEquals(Optional.of(expected), result);
        verify(store).write(expected);
        verifyNoMoreInteractions(store);
    }

    private RepositoryPush push(long id, long seconds) {
        return new RepositoryPush(id, "a".repeat(40), "b".repeat(40), Instant.ofEpochSecond(seconds).toString());
    }
}
