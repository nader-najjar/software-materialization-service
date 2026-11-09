package io.materializer.shared;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.shared.models.PipelineStructure;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineStructureStoreTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);

    private final PipelineStructureStore pipelineStructureStore = new PipelineStructureStore(
            gitHub,
            new EnvironmentVariables("test", "owner/registry", "owner/service", "owner/runner")
    );

    @Test
    void resolvesThePipelineStructuresBranchToItsCommit() throws IOException {
        GHBranch branch = mock(GHBranch.class);
        when(branch.getSHA1()).thenReturn("a".repeat(40));
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
        when(repository.getBranches()).thenReturn(Map.of("pipeline-structures", branch));

        assertEquals("a".repeat(40), pipelineStructureStore.getOrCreateBranchHeadSHA());
        verify(repository, never()).createTree();
        verify(repository, never()).createRef(anyString(), anyString());
        verify(repository).getBranches();
    }

    @Test
    void readsEachMaterializerAtTheCurrentBranchHead() throws IOException {
        GHBranch branch = mock(GHBranch.class);
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
        when(repository.getBranches()).thenReturn(Map.of("pipeline-structures", branch));
        when(branch.getSHA1()).thenReturn("a".repeat(40), "b".repeat(40));

        when(repository.getTreeRecursive("a".repeat(40), 1)).thenReturn(mock(GHTree.class));

        when(repository.getTreeRecursive("b".repeat(40), 1)).thenReturn(mock(GHTree.class));

        assertTrue(pipelineStructureStore.read("owner/first").isEmpty());
        assertTrue(pipelineStructureStore.read("owner/second").isEmpty());

        verify(repository).getTreeRecursive("a".repeat(40), 1);
        verify(repository).getTreeRecursive("b".repeat(40), 1);
    }

    @Test
    void createsStorageWhenTheBranchDoesNotExist() throws IOException {
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
        when(repository.getBranches()).thenReturn(Map.of());

        stubStorageCreation();

        assertEquals("b".repeat(40), pipelineStructureStore.getOrCreateBranchHeadSHA());
        verify(repository).createRef("refs/heads/pipeline-structures", "b".repeat(40));
    }

    @Test
    void doesNotConfuseSimilarlyNamedBranchesWithPipelineStructures() throws IOException {
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
        when(repository.getBranches()).thenReturn(Map.of("pipeline-structures-backup", mock(GHBranch.class)));

        stubStorageCreation();

        assertEquals("b".repeat(40), pipelineStructureStore.getOrCreateBranchHeadSHA());
        verify(repository).createRef("refs/heads/pipeline-structures", "b".repeat(40));
    }

    @Test
    void doesNotTreatAnInaccessibleRepositoryAsAnAbsentBranch() throws IOException {
        IOException failure = new GHFileNotFoundException("Not Found");
        when(gitHub.getRepository("owner/service")).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                pipelineStructureStore::getOrCreateBranchHeadSHA
        );

        assertEquals("Could not resolve pipeline-structures branch in owner/service", exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    @Test
    void propagatesBranchListingFailures() throws IOException {
        IOException failure = new IOException("Request failed");
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
        when(repository.getBranches()).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                pipelineStructureStore::getOrCreateBranchHeadSHA
        );

        assertSame(failure, exception.getCause());
    }

    @Test
    void doesNotOverwriteStorageWhenCreationFails() throws IOException {
        IOException failure = new IOException("Reference already exists");
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
        when(repository.getBranches()).thenReturn(Map.of());
        stubStorageCreation();
        when(repository.createRef("refs/heads/pipeline-structures", "b".repeat(40))).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                pipelineStructureStore::getOrCreateBranchHeadSHA
        );

        assertSame(failure, exception.getCause());
        verify(repository, times(1)).createRef("refs/heads/pipeline-structures", "b".repeat(40));
    }

    @Test
    void readsAndValidatesTheStoredStructureAtTheRequestedRevision() throws IOException {
        PipelineStructure expected = new PipelineStructure(
                new RepositorySnapshot("owner/pipeline", "main", "a".repeat(40), 42),
                new RepositorySnapshot("owner/service", "stable", "b".repeat(40), 42),
                List.of()
        );

        String json = JsonMapperUtility.MAPPER.writeValueAsString(expected);
        stubStructureFile(json);
        assertEquals(expected, pipelineStructureStore.read("Owner/Pipeline").orElseThrow());
    }

    @Test
    void returnsEmptyOnlyWhenTheFileIsAbsentFromACompleteTree() throws IOException {
        GHTree tree = stubStructureTree();
        assertTrue(pipelineStructureStore.read("owner/pipeline").isEmpty());

        when(tree.isTruncated()).thenReturn(true);
        assertThrows(UncheckedIOException.class, () -> pipelineStructureStore.read("owner/pipeline"));
    }

    @Test
    void propagatesInaccessibleRevisions() throws IOException {
        stubStorageBranch();
        IOException failure = new GHFileNotFoundException("Revision unavailable");
        when(repository.getTreeRecursive("c".repeat(40), 1)).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> pipelineStructureStore.read("owner/pipeline")
        );

        assertSame(failure, exception.getCause());
    }

    @Test
    void rejectsInvalidStructureContent() throws IOException {
        stubStructureFile("{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> pipelineStructureStore.read("owner/pipeline")
        );
    }

    @Test
    void rejectsPipelineStructuresForADifferentMaterializer() throws IOException {
        PipelineStructure structure = new PipelineStructure(
                new RepositorySnapshot("owner/other", "main", "a".repeat(40), 42),
                new RepositorySnapshot("owner/service", "stable", "b".repeat(40), 42),
                List.of()
        );

        stubStructureFile(JsonMapperUtility.MAPPER.writeValueAsString(structure));
        assertThrows(
                IllegalArgumentException.class,
                () -> pipelineStructureStore.read("owner/pipeline")
        );
    }

    @Test
    void writesOnTheCurrentBranchHeadAndReadsBackThePublishedStructure() throws IOException {
        GHTree originalTree = stubStructureTree();
        String parentSHA = "c".repeat(40);
        String nextSHA = "d".repeat(40);
        PipelineStructure structure = new PipelineStructure(
                new RepositorySnapshot("Owner/Pipeline", "main", "a".repeat(40), 42),
                new RepositorySnapshot("owner/service", "main", "b".repeat(40), 43),
                List.of()
        );
        String path = "pipeline-structures/owner/pipeline.json";
        String json = JsonMapperUtility.MAPPER.writeValueAsString(structure);

        GHCommit parentCommit = mock(GHCommit.class);
        when(repository.getCommit(parentSHA)).thenReturn(parentCommit);
        when(parentCommit.getTree()).thenReturn(originalTree);
        when(originalTree.getSha()).thenReturn("original-tree");
        GHTreeBuilder treeBuilder = mock(GHTreeBuilder.class, RETURNS_SELF);
        GHTree updatedTree = mock(GHTree.class);
        when(repository.createTree()).thenReturn(treeBuilder);
        when(treeBuilder.create()).thenReturn(updatedTree);
        when(updatedTree.getSha()).thenReturn("updated-tree");

        GHCommitBuilder commitBuilder = mock(GHCommitBuilder.class, RETURNS_SELF);
        GHCommit nextCommit = mock(GHCommit.class);
        when(repository.createCommit()).thenReturn(commitBuilder);
        when(commitBuilder.create()).thenReturn(nextCommit);
        when(nextCommit.getSHA1()).thenReturn(nextSHA);
        GHRef branchRef = mock(GHRef.class);
        when(repository.getRef("heads/pipeline-structures")).thenReturn(branchRef);
        doThrow(new IOException("Branch update rejected")).doNothing().when(branchRef).updateTo(nextSHA, false);

        assertThrows(UncheckedIOException.class, () -> pipelineStructureStore.write(structure));
        assertTrue(pipelineStructureStore.read("Owner/Pipeline").isEmpty());

        pipelineStructureStore.write(structure);
        verify(treeBuilder, times(2)).baseTree("original-tree");
        verify(treeBuilder, times(2)).add(path, json, false);
        verify(commitBuilder, times(2)).parent(parentSHA);
        verify(commitBuilder, times(2)).tree("updated-tree");
        verify(branchRef, times(2)).updateTo(nextSHA, false);
        verify(repository, times(3)).getBranches();

        GHBranch updatedBranch = mock(GHBranch.class);
        when(updatedBranch.getSHA1()).thenReturn(nextSHA);
        when(repository.getBranches()).thenReturn(Map.of("pipeline-structures", updatedBranch));
        when(repository.getTreeRecursive(nextSHA, 1)).thenReturn(updatedTree);
        GHTreeEntry entry = mock(GHTreeEntry.class);
        when(updatedTree.getEntry(path)).thenReturn(entry);
        when(entry.getType()).thenReturn("blob");
        when(entry.readAsBlob()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(structure, pipelineStructureStore.read("owner/pipeline").orElseThrow());
    }

    private GHTree stubStructureTree() throws IOException {
        GHTree tree = mock(GHTree.class);
        stubStorageBranch();
        when(repository.getTreeRecursive("c".repeat(40), 1)).thenReturn(tree);
        return tree;
    }

    private void stubStructureFile(String json) throws IOException {
        GHTree tree = stubStructureTree();
        GHTreeEntry entry = mock(GHTreeEntry.class);
        when(tree.getEntry("pipeline-structures/owner/pipeline.json")).thenReturn(entry);
        when(entry.getType()).thenReturn("blob");
        when(entry.readAsBlob()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private void stubStorageBranch() throws IOException {
        GHBranch branch = mock(GHBranch.class);
        when(branch.getSHA1()).thenReturn("c".repeat(40));
        when(repository.getBranches()).thenReturn(Map.of("pipeline-structures", branch));
        when(gitHub.getRepository("owner/service")).thenReturn(repository);
    }

    private void stubStorageCreation() throws IOException {
        GHTreeBuilder treeBuilder = mock(GHTreeBuilder.class);
        GHTree tree = mock(GHTree.class);
        GHCommitBuilder commitBuilder = mock(GHCommitBuilder.class);
        GHCommit commit = mock(GHCommit.class);
        when(repository.createTree()).thenReturn(treeBuilder);
        when(treeBuilder.add(".gitkeep", "", false)).thenReturn(treeBuilder);
        when(treeBuilder.create()).thenReturn(tree);
        when(tree.getSha()).thenReturn("c".repeat(40));
        when(repository.createCommit()).thenReturn(commitBuilder);
        when(commitBuilder.message("Initialize pipeline structure storage")).thenReturn(commitBuilder);
        when(commitBuilder.tree("c".repeat(40))).thenReturn(commitBuilder);
        when(commitBuilder.create()).thenReturn(commit);
        when(commit.getSHA1()).thenReturn("b".repeat(40));
    }
}
