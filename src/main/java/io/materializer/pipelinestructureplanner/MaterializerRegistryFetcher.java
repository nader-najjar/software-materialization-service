package io.materializer.pipelinestructureplanner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.pipelinestructureplanner.models.MaterializerRegistry;
import io.materializer.shared.utilities.JsonSanitizer;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class MaterializerRegistryFetcher {

    private final GitHub gitHub;
    private final String registryRepository;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public MaterializerRegistryFetcher(GitHub gitHub, EnvironmentVariables environmentVariables) {
        this.gitHub = gitHub;
        this.registryRepository = environmentVariables.materializerRegistryRepository();
    }

    public MaterializerRegistry fetch() {
        String registryJson = readRegistryJson();

        return JsonSanitizer.parseAndValidate(registryJson, MaterializerRegistry.class);
    }

    private String readRegistryJson() {
        try {
            GHRepository repository = gitHub.getRepository(registryRepository);
            GHContent registryFileContent = repository.getFileContent("materializer-registry.json");

            return readContent(registryFileContent);

        } catch (IOException exception) {
            String message = "Could not read materializer registry from %s".formatted(registryRepository);

            throw new UncheckedIOException(message, exception);
        }
    }

    private String readContent(GHContent registryFileContent) throws IOException {
        try (InputStream content = registryFileContent.read()) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
