package io.materializer;

import java.net.http.HttpClient;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import io.materializer.composition.StrictGuiceModule;
import io.materializer.environment.EnvironmentVariables;
import io.materializer.execution.Executor;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GitHub;

import static org.mockito.Mockito.mock;

class ApplicationCompositionTest {

    @Test
    void assemblesApplication() {
        EnvironmentVariables environmentVariables = mock(EnvironmentVariables.class);
        GitHub gitHub = mock(GitHub.class);
        HttpClient httpClient = mock(HttpClient.class);

        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                new StrictGuiceModule(),
                binder -> {
                    binder.bind(EnvironmentVariables.class).toInstance(environmentVariables);
                    binder.bind(GitHub.class).toInstance(gitHub);
                    binder.bind(HttpClient.class).toInstance(httpClient);
                }
        );

        injector.getInstance(Executor.class);
    }
}
