package io.materializer.composition;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.materializer.environment.EnvironmentVariables;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;

public class EnvironmentModule extends AbstractModule {

    @Provides
    @Singleton
    EnvironmentVariables provideEnvironmentVariables() {
        EnvironmentVariables environmentVariables = JsonMapperUtility.MAPPER.convertValue(
                System.getenv(), EnvironmentVariables.class
        );
        // Entry points need different environment values, so Hibernate validation lives with their consumers.
        // Guice always creates the GitHub client, making its token required for every entry point.
        HibernateValidatorUtility.validateProperties(environmentVariables, "githubToken");
        return environmentVariables;
    }
}
