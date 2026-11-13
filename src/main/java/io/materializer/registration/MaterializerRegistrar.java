package io.materializer.registration;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.materializerregistrystore.models.MaterializerRegistrySnapshot;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaterializerRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterializerRegistrar.class);
    private static final String CLIENT_ID_VARIABLE = "BUILD_PUBLISHER_APP_CLIENT_ID";

    private final GitHub gitHub;
    private final MaterializerRegistryStore registryStore;
    private final GitHubSecretSetter secretSetter;
    private final EnvironmentVariables environment;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public MaterializerRegistrar(
            GitHub gitHub,
            MaterializerRegistryStore registryStore,
            GitHubSecretSetter secretSetter,
            EnvironmentVariables environment
    ) {
        this.gitHub = gitHub;
        this.registryStore = registryStore;
        this.secretSetter = secretSetter;
        this.environment = environment;
    }

    public void register(String repositoryName) {
        MaterializerRegistrySnapshot snapshot = registryStore.readSnapshot();
        rejectDuplicate(snapshot.registry(), repositoryName);
        configurePublisher(repositoryName);
        registryStore.addRegistration(snapshot, repositoryName);
        LOGGER.info("Registered {}", repositoryName);
    }

    private void rejectDuplicate(MaterializerRegistry registry, String repositoryName) {
        boolean registered = registry.registrations().stream()
                .anyMatch(registration -> registration.repositoryName().equalsIgnoreCase(repositoryName));
        if (registered) {
            throw new IllegalArgumentException("Materializer is already registered: " + repositoryName);
        }
    }

    private void configurePublisher(String repositoryName) {
        try {
            GHRepository repository = gitHub.getRepository(repositoryName);
            setClientID(repository);

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not configure Publisher client ID for " + repositoryName, exception);
        }
        
        secretSetter.setBuildPublisherPrivateKey(repositoryName, environment.buildPublisherAppPrivateKey());
    }

    private void setClientID(GHRepository repository) throws IOException {
        try {
            repository.getVariable(CLIENT_ID_VARIABLE).set().value(environment.buildPublisherAppClientID());

        } catch (GHFileNotFoundException exception) {
            repository.createVariable(CLIENT_ID_VARIABLE, environment.buildPublisherAppClientID());
        }
    }

}
