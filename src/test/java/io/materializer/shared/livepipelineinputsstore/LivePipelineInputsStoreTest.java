package io.materializer.shared.livepipelineinputsstore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import io.materializer.environment.EnvironmentVariables;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.JsonMapperUtility;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LivePipelineInputsStoreTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHTree tree = mock(GHTree.class);
    private final LivePipelineInputsStore store = new LivePipelineInputsStore(
            gitHub,
            new EnvironmentVariables("test", "owner/registry", "owner/service", null, null)
    );

    @Test
    void failsWhenMainIsAbsentInsteadOfInitializingStorage() throws IOException {
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
        when(repository.getBranch("main")).thenThrow(new GHFileNotFoundException());

        assertThrows(UncheckedIOException.class, () -> store.read("owner/materializer"));
        LivePipelineInputs inputs = new LivePipelineInputs(
                new RepositorySnapshot("owner/materializer", "main", "a".repeat(40), 1L),
                List.of()
        );
        assertThrows(UncheckedIOException.class, () -> store.write(inputs));
        verify(repository, never()).createRef(anyString(), anyString());
    }

    @Test
    void returnsEmptyWhenInputsFileIsAbsent() throws IOException {
        prepareBranch();

        assertEquals(Optional.empty(), store.read("owner/materializer"));
    }

    @Test
    void readsInputsAtTheBranchCommitUsingACaseInsensitivePath() throws IOException {
        prepareBranch();
        LivePipelineInputs inputs = new LivePipelineInputs(
                new RepositorySnapshot("owner/materializer", "main", "a".repeat(40), 1L),
                List.of()
        );
        prepareFile(JsonMapperUtility.MAPPER.writeValueAsString(inputs));

        assertEquals(Optional.of(inputs), store.read("OWNER/Materializer"));
    }

    @Test
    void rejectsInvalidInputs() throws IOException {
        prepareBranch();
        prepareFile("{}");

        assertThrows(IllegalArgumentException.class, () -> store.read("owner/materializer"));
    }

    @Test
    void rejectsInputsForAnotherMaterializer() throws IOException {
        prepareBranch();
        LivePipelineInputs inputs = new LivePipelineInputs(
                new RepositorySnapshot("owner/other", "main", "a".repeat(40), 1L),
                List.of()
        );
        prepareFile(JsonMapperUtility.MAPPER.writeValueAsString(inputs));

        assertThrows(IllegalArgumentException.class, () -> store.read("owner/materializer"));
    }

    @Test
    void doesNotTreatATruncatedTreeAsMissingInputs() throws IOException {
        prepareBranch();
        when(tree.isTruncated()).thenReturn(true);

        assertThrows(UncheckedIOException.class, () -> store.read("owner/materializer"));
    }

    @Test
    void rejectsANonFileEntry() throws IOException {
        prepareBranch();
        GHTreeEntry entry = mock(GHTreeEntry.class);
        when(tree.getEntry("live-pipeline-inputs/owner/materializer.json")).thenReturn(entry);
        when(entry.getType()).thenReturn("tree");

        assertThrows(UncheckedIOException.class, () -> store.read("owner/materializer"));
    }

    @Test
    void writesInputsOnMainPreservingTheExistingTree() throws IOException {
        prepareBranch();
        GHTreeBuilder treeBuilder = prepareWrite();
        GHRef ref = mock(GHRef.class);
        when(repository.getRef("heads/main")).thenReturn(ref);
        LivePipelineInputs inputs = new LivePipelineInputs(
                new RepositorySnapshot("Owner/Materializer", "main", "a".repeat(40), 1L),
                List.of()
        );

        store.write(inputs);

        verify(treeBuilder).baseTree("base-tree");
        verify(treeBuilder).add(
                "live-pipeline-inputs/owner/materializer.json",
                JsonMapperUtility.MAPPER.writeValueAsString(inputs),
                false
        );
        verify(ref).updateTo("new-commit", false);
    }

    @Test
    void rejectsAConcurrentMainUpdateWithoutForcingTheRef() throws IOException {
        prepareBranch();
        prepareWrite();
        GHRef ref = mock(GHRef.class);
        when(repository.getRef("heads/main")).thenReturn(ref);
        doThrow(new IOException("Non-fast-forward update")).when(ref).updateTo("new-commit", false);
        LivePipelineInputs inputs = new LivePipelineInputs(
                new RepositorySnapshot("owner/materializer", "main", "a".repeat(40), 1L),
                List.of()
        );

        assertThrows(UncheckedIOException.class, () -> store.write(inputs));

        verify(ref).updateTo("new-commit", false);
        verify(ref, never()).updateTo(anyString(), org.mockito.ArgumentMatchers.eq(true));
    }

    private GHTreeBuilder prepareWrite() throws IOException {
        GHCommit parent = mock(GHCommit.class);
        when(repository.getCommit("storage-commit")).thenReturn(parent);
        when(parent.getTree()).thenReturn(tree);
        when(tree.getSha()).thenReturn("base-tree");
        GHTreeBuilder treeBuilder = mock(GHTreeBuilder.class, RETURNS_SELF);
        when(repository.createTree()).thenReturn(treeBuilder);
        GHTree nextTree = mock(GHTree.class);
        when(treeBuilder.create()).thenReturn(nextTree);
        when(nextTree.getSha()).thenReturn("next-tree");
        GHCommitBuilder commitBuilder = mock(GHCommitBuilder.class, RETURNS_SELF);
        when(repository.createCommit()).thenReturn(commitBuilder);
        GHCommit commit = mock(GHCommit.class);
        when(commitBuilder.create()).thenReturn(commit);
        when(commit.getSHA1()).thenReturn("new-commit");
        return treeBuilder;
    }

    private void prepareBranch() throws IOException {
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
        GHBranch branch = mock(GHBranch.class);
        when(branch.getSHA1()).thenReturn("storage-commit");
        when(repository.getBranch("main")).thenReturn(branch);
        when(repository.getTreeRecursive("storage-commit", 1)).thenReturn(tree);
    }

    private void prepareFile(String json) throws IOException {
        GHTreeEntry entry = mock(GHTreeEntry.class);
        when(tree.getEntry("live-pipeline-inputs/owner/materializer.json")).thenReturn(entry);
        when(entry.getType()).thenReturn("blob");
        when(entry.readAsBlob()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
