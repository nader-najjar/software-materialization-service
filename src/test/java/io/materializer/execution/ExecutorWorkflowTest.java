package io.materializer.execution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.materializer.pipelinestructureplanner.PipelineStructurePlanner;
import io.materializer.shared.PipelineDeclarationFetcher;
import io.materializer.shared.PipelineStructureStore;
import io.materializer.shared.models.DeclaredPipelineInput;
import io.materializer.shared.models.DeclaredPipelineStep;
import io.materializer.shared.models.PipelineDeclaration;
import io.materializer.shared.models.PipelineStructure;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.ResourceUtility;
import io.materializer.shared.utilities.YamlMapperUtility;
import io.materializer.workflowgeneration.BuildJobGenerator;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GitHub;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ExecutorWorkflowTest {

    private final PipelineStructurePlanner planner = mock(PipelineStructurePlanner.class);
    private final PipelineStructureStore store = mock(PipelineStructureStore.class);
    private final PipelineDeclarationFetcher fetcher = mock(PipelineDeclarationFetcher.class);
    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHTreeBuilder treeBuilder = mock(GHTreeBuilder.class, RETURNS_SELF);
    private final GHCommitBuilder commitBuilder = mock(GHCommitBuilder.class, RETURNS_SELF);
    private final PipelineStructure structure = new PipelineStructure(
            new RepositorySnapshot("owner/materializer", "main", "a".repeat(40), 1),
            List.of(new RepositorySnapshot("owner/source", "main", "b".repeat(40), 2)),
            false
    );
    private final Executor executor = new Executor(
            planner,
            store,
            new WorkflowGenerator(fetcher, new BuildJobGenerator()),
            new WorkflowPublisher(gitHub)
    );

    @Test
    void publishesGeneratedWorkflowAndActionsBeforeMarkingDispatched() throws IOException {
        preparePublication();
        executor.execute(new String[] {"{\"operation\":\"materialize-next\"}"});

        InOrder order = inOrder(store, treeBuilder, repository);
        ArgumentCaptor<String> yaml = ArgumentCaptor.forClass(String.class);
        order.verify(store).write(structure);
        order.verify(treeBuilder).add(eq(".github/workflows/materialize.yml"), yaml.capture(), eq(false));
        verifyPublishedActions(order);
        order.verify(treeBuilder).create();
        order.verify(repository).createRef("refs/heads/execution", "published-commit");
        order.verify(store).write(structure.withDispatchMarked());
        verifyNoMoreInteractions(store);

        JsonNode workflow = YamlMapperUtility.MAPPER.readTree(yaml.getValue());
        JsonNode job = workflow.path("jobs").path("step_1");
        JsonNode row = job.path("strategy").path("matrix").path("include").get(0);
        assertEquals("owner/source", row.path("repositoryName").asText());
        assertEquals("b".repeat(40), row.path("commitSHA").asText());
        assertEquals(6, job.path("steps").size());
    }

    @Test
    void leavesStructureUndispatchedWhenExecutionBranchPublicationFails() throws IOException {
        preparePublication();
        IOException failure = new IOException("GitHub unavailable");
        when(repository.createRef("refs/heads/execution", "published-commit")).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> executor.execute(new String[] {"{\"operation\":\"materialize-next\"}"})
        );

        assertEquals(failure, exception.getCause());
        verify(treeBuilder).create();
        verify(store).write(structure);
        verifyNoMoreInteractions(store);
    }

    private void preparePublication() throws IOException {
        when(planner.planNextStructures()).thenReturn(List.of(structure));
        DeclaredPipelineInput input = new DeclaredPipelineInput(
                "owner/source",
                "main",
                "universal",
                List.of("x86_64-linux")
        );
        PipelineDeclaration declaration = new PipelineDeclaration(
                List.of(input),
                List.of(new DeclaredPipelineStep("Build", "build"))
        );
        when(fetcher.fetch(structure.materializerRepositorySnapshot())).thenReturn(declaration);
        when(gitHub.getRepository("owner/materializer")).thenReturn(repository);
        when(repository.getBranches()).thenReturn(Map.of());
        when(repository.createTree()).thenReturn(treeBuilder);
        when(repository.createCommit()).thenReturn(commitBuilder);
        GHTree tree = mock(GHTree.class);
        when(treeBuilder.create()).thenReturn(tree);
        when(tree.getSha()).thenReturn("workflow-tree");
        GHCommit commit = mock(GHCommit.class);
        when(commitBuilder.create()).thenReturn(commit);
        when(commit.getSHA1()).thenReturn("published-commit");
    }

    private void verifyPublishedActions(InOrder order) throws IOException {
        for (String resource : List.of(
                "actions/reject-manual-retries/action.yml",
                "actions/reject-manual-retries/check.sh",
                "actions/universal-build/action.yml"
        )) {
            order.verify(treeBuilder).add(".github/" + resource, ResourceUtility.readString("/" + resource), false);
        }
    }
}
