package io.materializer.shared.livepipelinedeclarationfetcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.JsonSanitizer;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class LivePipelineDeclarationFetcher {

    private final GitHub gitHub;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public LivePipelineDeclarationFetcher(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    public LivePipelineDeclaration fetchAndValidate(RepositorySnapshot materializerSnapshot) {
        String pipelineJson = readPipelineJson(materializerSnapshot.repositoryName(), materializerSnapshot.commitSHA());
        return JsonSanitizer.parseAndValidate(pipelineJson, LivePipelineDeclaration.class);
    }

    private String readPipelineJson(String repositoryName, String commitSHA) {
        try {
            GHRepository repository = gitHub.getRepository(repositoryName);
            GHContent file = repository.getFileContent("pipeline.json", commitSHA);
            return readContent(file);

        } catch (IOException exception) {
            String message = "Could not read pipeline.json from %s at %s".formatted(repositoryName, commitSHA);
            throw new UncheckedIOException(message, exception);
        }
    }

    private String readContent(GHContent file) throws IOException {
        try (InputStream content = file.read()) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
