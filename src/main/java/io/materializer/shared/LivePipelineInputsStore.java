package io.materializer.shared;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.shared.models.LivePipelineInputs;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.shared.utilities.JsonSanitizer;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;

public final class LivePipelineInputsStore {

    private final GitHub gitHub;
    private final String storageRepository;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public LivePipelineInputsStore(GitHub gitHub, EnvironmentVariables environmentVariables) {
        this.gitHub = gitHub;
        this.storageRepository = environmentVariables.livePipelineInputsStoreRepository();
    }

    public Optional<LivePipelineInputs> read(String materializerRepository) {
        String path = inputsFilePath(materializerRepository);
        Optional<String> content;
        try {
            GHRepository repository = gitHub.getRepository(storageRepository);
            GHBranch branch = repository.getBranches().get("live-pipeline-inputs");
            if (branch == null) { return Optional.empty(); }

            GHTree tree = repository.getTreeRecursive(branch.getSHA1(), 1);
            content = readInputsFile(tree, path);

        } catch (IOException exception) {
            String message = "Could not read %s from %s".formatted(path, storageRepository);
            throw new UncheckedIOException(message, exception);
        }

        return content.map(json -> parseInputs(json, materializerRepository));
    }

    public void write(LivePipelineInputs inputs) {
        String materializerRepository = inputs.materializerRepositorySnapshot().repositoryName();
        String inputsFilePath = inputsFilePath(materializerRepository);
        String parentCommitSHA = getOrCreateBranchHeadSHA();

        try {
            GHRepository repository = gitHub.getRepository(storageRepository);
            String baseTreeSHA = repository.getCommit(parentCommitSHA).getTree().getSha();
            String inputsJson = JsonMapperUtility.MAPPER.writeValueAsString(inputs);
            GHTree updatedTree = repository.createTree()
                    .baseTree(baseTreeSHA)
                    .add(inputsFilePath, inputsJson, false)
                    .create();
            if (baseTreeSHA.equals(updatedTree.getSha())) { return; }

            GHCommit commit = repository.createCommit()
                    .message("Store pipeline inputs for " + materializerRepository)
                    .parent(parentCommitSHA)
                    .tree(updatedTree.getSha())
                    .create();

            String nextCommitSHA = commit.getSHA1();
            repository.getRef("heads/live-pipeline-inputs").updateTo(nextCommitSHA, false);

        } catch (IOException exception) {
            String message = "Could not write %s in %s from %s".formatted(
                    inputsFilePath,
                    storageRepository,
                    parentCommitSHA
            );
            throw new UncheckedIOException(message, exception);
        }
    }

    private static String inputsFilePath(String materializerRepository) {
        String[] repositoryParts = materializerRepository.toLowerCase(Locale.ROOT).split("/", 2);
        String owner = repositoryParts[0];
        String repositoryName = repositoryParts[1];
        return "live-pipeline-inputs/%s/%s.json".formatted(owner, repositoryName);
    }

    private Optional<String> readInputsFile(GHTree tree, String path) throws IOException {
        GHTreeEntry entry = tree.getEntry(path);
        if (entry == null && tree.isTruncated()) {
            throw new IOException("Incomplete storage tree while looking for " + path);
        }

        if (entry == null) { return Optional.empty(); }

        if (!"blob".equals(entry.getType())) {
            throw new IOException("Expected a live pipeline inputs file at " + path);
        }

        try (InputStream content = entry.readAsBlob()) {
            return Optional.of(new String(content.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private LivePipelineInputs parseInputs(String json, String materializerRepository) {
        LivePipelineInputs inputs = JsonSanitizer.parseAndValidate(json, LivePipelineInputs.class);

        if (!inputs.materializerRepositorySnapshot().repositoryName().equalsIgnoreCase(materializerRepository)) {
            String message = "Expected live pipeline inputs for %s, found %s".formatted(
                    materializerRepository,
                    inputs.materializerRepositorySnapshot().repositoryName()
            );

            throw new IllegalArgumentException(message);
        }

        return inputs;
    }

    String getOrCreateBranchHeadSHA() {
        try {
            GHRepository repository = gitHub.getRepository(storageRepository);
            GHBranch branch = repository.getBranches().get("live-pipeline-inputs");
            if (branch != null) { return branch.getSHA1(); }

            return createLivePipelineInputsBranch(repository);

        } catch (IOException exception) {
            String message = "Could not resolve live-pipeline-inputs branch in %s".formatted(storageRepository);
            throw new UncheckedIOException(message, exception);
        }
    }

    private static String createLivePipelineInputsBranch(GHRepository repository) throws IOException {
        GHTree tree = repository.createTree()
                .add(".gitkeep", "", false)
                .create();

        GHCommit commit = repository.createCommit()
                .message("Initialize live pipeline inputs storage")
                .tree(tree.getSha())
                .create();

        repository.createRef("refs/heads/live-pipeline-inputs", commit.getSHA1());
        return commit.getSHA1();
    }
}
