package io.materializer.publisherconfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.EnvironmentVariables;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class PublisherConfigurator {

    private static final String CLIENT_ID_VARIABLE = "BUILD_PUBLISHER_APP_CLIENT_ID";

    private final GitHub gitHub;
    private final GitHubSecretSetter secretSetter;
    private final EnvironmentVariables environment;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public PublisherConfigurator(
            GitHub gitHub,
            GitHubSecretSetter secretSetter,
            EnvironmentVariables environment
    ) {
        this.gitHub = gitHub;
        this.secretSetter = secretSetter;
        this.environment = environment;
    }

    public void configure(String repositoryName) {
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
