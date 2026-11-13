package io.materializer.execution;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.EnvironmentVariables;
import io.materializer.publisherconfiguration.PublisherConfigurator;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterExecutor.class);

    private final EnvironmentVariables environmentVariables;
    private final MaterializerRegistryStore registryStore;
    private final PublisherConfigurator publisherConfigurator;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the injected dependencies")
    @Inject
    public RegisterExecutor(
            EnvironmentVariables environmentVariables,
            MaterializerRegistryStore registryStore,
            PublisherConfigurator publisherConfigurator
    ) {
        this.environmentVariables = environmentVariables;
        this.registryStore = registryStore;
        this.publisherConfigurator = publisherConfigurator;
    }

    public void execute(String repositoryName) {
        HibernateValidatorUtility.validateProperties(
                environmentVariables,
                "materializerRegistryRepository",
                "buildPublisherAppClientID",
                "buildPublisherAppPrivateKey"
        );

        MaterializerRegistrySnapshot snapshot = registryStore.readSnapshot();

        for (MaterializerRegistration registration : snapshot.registry().registrations()) {
            if (registration.repositoryName().equalsIgnoreCase(repositoryName)) {
                throw new IllegalArgumentException("Materializer is already registered: " + repositoryName);
            }
        }

        publisherConfigurator.configure(repositoryName);

        registryStore.addRegistration(snapshot, repositoryName);
        LOGGER.info("Registered {}", repositoryName);
    }

}
