package io.materializer.pipelinestructureplanner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.pipelinestructureplanner.models.DeclaredPipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.JsonSanitizer;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class DeclaredPipelineInputsFetcher {

    private final GitHub gitHub;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public DeclaredPipelineInputsFetcher(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    public DeclaredPipelineInputs fetch(RepositorySnapshot materializerSnapshot) {
        String pipelineJson = readPipelineJson(materializerSnapshot.repositoryName(), materializerSnapshot.commitSHA());
        return JsonSanitizer.parseAndValidate(pipelineJson, DeclaredPipelineInputs.class);
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
