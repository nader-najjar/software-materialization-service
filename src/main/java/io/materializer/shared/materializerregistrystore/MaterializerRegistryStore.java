package io.materializer.shared.materializerregistrystore;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.EnvironmentVariables;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.shared.utilities.JsonSanitizer;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class MaterializerRegistryStore {

    private final GitHub gitHub;
    private final String registryRepository;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public MaterializerRegistryStore(GitHub gitHub, EnvironmentVariables environmentVariables) {
        this.gitHub = gitHub;
        this.registryRepository = environmentVariables.materializerRegistryRepository();
    }

    public MaterializerRegistry read() {
        return readSnapshot().registry();
    }

    public MaterializerRegistrySnapshot readSnapshot() {
        GHContent content = fetchContent();
        String json = readContent(content);
        MaterializerRegistry registry = JsonSanitizer.parseAndValidate(json, MaterializerRegistry.class);
        return new MaterializerRegistrySnapshot(registry, content.getSha());
    }

    public void addRegistration(MaterializerRegistrySnapshot snapshot, String repositoryName) {
        List<MaterializerRegistration> registrations = new ArrayList<>(snapshot.registry().registrations());
        registrations.add(new MaterializerRegistration(repositoryName));
        write(new MaterializerRegistry(registrations), snapshot.fileSHA(), repositoryName);
    }

    private void write(MaterializerRegistry registry, String fileSHA, String repositoryName) {
        try {
            byte[] json = JsonMapperUtility.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(registry);
            gitHub.getRepository(registryRepository).createContent()
                    .path("materializer-registry.json")
                    .sha(fileSHA)
                    .content(json)
                    .message("Register " + repositoryName)
                    .commit();

        } catch (IOException exception) {
            String message = "Could not update materializer registry in " + registryRepository;
            throw new UncheckedIOException(message, exception);
        }
    }

    private GHContent fetchContent() {
        try {
            GHRepository repository = gitHub.getRepository(registryRepository);
            return repository.getFileContent("materializer-registry.json");

        } catch (IOException exception) {
            String message = "Could not read materializer registry from %s".formatted(registryRepository);

            throw new UncheckedIOException(message, exception);
        }
    }

    private String readContent(GHContent registryFileContent) {
        try (InputStream content = registryFileContent.read()) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException exception) {
            String message = "Could not read materializer registry from " + registryRepository;
            throw new UncheckedIOException(message, exception);
        }
    }
}
