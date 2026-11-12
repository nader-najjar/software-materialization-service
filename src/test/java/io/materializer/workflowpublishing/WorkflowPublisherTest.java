package io.materializer.workflowpublishing;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.materializer.shared.utilities.ResourceUtility;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GitHub;

import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowPublisherTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHTreeBuilder treeBuilder = mock(GHTreeBuilder.class, RETURNS_SELF);
    private final GHCommitBuilder commitBuilder = mock(GHCommitBuilder.class, RETURNS_SELF);
    private final WorkflowPublisher workflowPublisher = new WorkflowPublisher(gitHub);

    @Test
    void publishesFirstWorkflowWhenCreatingExecutionBranch() throws IOException {
        preparePublication();
        when(repository.getBranches()).thenReturn(Map.of());

        workflowPublisher.publish("owner/materializer", "generated yaml");
        verifyActionResources();

        verify(treeBuilder).add(".github/workflows/materialize.yml", "generated yaml", false);
        verify(commitBuilder).tree("workflow-tree");
        verify(repository).createRef("refs/heads/execution", "published-commit");
    }

    @Test
    void pushesNewCommitEvenWhenWorkflowTreeIsUnchanged() throws IOException {
        preparePublication();
        GHBranch branch = mock(GHBranch.class);
        when(repository.getBranches()).thenReturn(Map.of("execution", branch));
        when(branch.getSHA1()).thenReturn("previous-commit");
        GHCommit parent = mock(GHCommit.class);
        when(repository.getCommit("previous-commit")).thenReturn(parent);
        GHTree previousTree = mock(GHTree.class);
        when(parent.getTree()).thenReturn(previousTree);
        when(previousTree.getSha()).thenReturn("workflow-tree");
        GHRef ref = mock(GHRef.class);
        when(repository.getRef("heads/execution")).thenReturn(ref);

        workflowPublisher.publish("owner/materializer", "generated yaml");
        verifyActionResources();

        verify(treeBuilder).baseTree("workflow-tree");
        verify(commitBuilder).parent("previous-commit");
        verify(ref).updateTo("published-commit", false);
    }

    private void verifyActionResources() throws IOException {
        List<String> paths = List.of(
                "actions/reject-manual-retries/action.yml",
                "actions/reject-manual-retries/check.sh",
                "actions/universal-build/action.yml",
                "actions/find-release-artifact/action.yml",
                "actions/find-release-artifact/find.cjs",
                "actions/publish-release-artifact/action.yml",
                "actions/publish-release-artifact/publish.cjs"
        );
        for (String path : paths) {
            verify(treeBuilder).add(".github/" + path, ResourceUtility.readString("/" + path), false);
        }
        verify(treeBuilder).create();
    }

    private void preparePublication() throws IOException {
        when(gitHub.getRepository("owner/materializer")).thenReturn(repository);
        when(repository.createTree()).thenReturn(treeBuilder);
        GHTree tree = mock(GHTree.class);
        when(treeBuilder.create()).thenReturn(tree);
        when(tree.getSha()).thenReturn("workflow-tree");
        when(repository.createCommit()).thenReturn(commitBuilder);
        GHCommit commit = mock(GHCommit.class);
        when(commitBuilder.create()).thenReturn(commit);
        when(commit.getSHA1()).thenReturn("published-commit");
    }
}
