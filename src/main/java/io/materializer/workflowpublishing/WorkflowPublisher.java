package io.materializer.workflowpublishing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.shared.utilities.ResourceUtility;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GitHub;

public final class WorkflowPublisher {

    private final GitHub gitHub;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public WorkflowPublisher(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    public void publish(String materializerRepositoryName, String workflowYaml) {
        try {
            GHRepository repository = gitHub.getRepository(materializerRepositoryName);
            GHBranch executionBranch = repository.getBranches().get("execution");
            GHTreeBuilder treeBuilder = repository.createTree();
            GHCommitBuilder commitBuilder = repository.createCommit();

            if (executionBranch != null) {
                String parentCommitSHA = executionBranch.getSHA1();
                treeBuilder.baseTree(repository.getCommit(parentCommitSHA).getTree().getSha());
                commitBuilder.parent(parentCommitSHA);
            }

            treeBuilder.add(".github/workflows/materialize.yml", workflowYaml, false);
            addActionResources(treeBuilder);
            GHTree tree = treeBuilder.create();
            GHCommit commit = commitBuilder.message("Publish materialization workflow").tree(tree.getSha()).create();

            if (executionBranch == null) {
                repository.createRef("refs/heads/execution", commit.getSHA1());

            } else {
                repository.getRef("heads/execution").updateTo(commit.getSHA1(), false);
            }

        } catch (IOException exception) {
            String message = "Could not publish workflow to %s on execution".formatted(materializerRepositoryName);
            throw new UncheckedIOException(message, exception);
        }
    }

    private void addActionResources(GHTreeBuilder treeBuilder) throws IOException {
        List<String> resourcePaths = List.of(
                "actions/reject-manual-retries/action.yml",
                "actions/reject-manual-retries/check.sh",
                "actions/universal-build/action.yml",
                "actions/find-release-artifact/action.yml",
                "actions/find-release-artifact/find.cjs",
                "actions/publish-release-artifact/action.yml",
                "actions/publish-release-artifact/publish.cjs"
        );

        for (String resourcePath : resourcePaths) {
            String content = ResourceUtility.readString("/" + resourcePath);
            treeBuilder.add(".github/" + resourcePath, content, false);
        }
    }

}
