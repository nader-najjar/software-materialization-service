package io.materializer.composition;

import java.io.IOException;
import java.net.http.HttpClient;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.materializer.environment.EnvironmentVariables;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class GitHubClientsModule extends AbstractModule {

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Provides
    @Singleton
    GitHub provideGitHubClient(EnvironmentVariables environmentVariables) throws IOException {
        return new GitHubBuilder()
                .withOAuthToken(environmentVariables.githubToken())
                .build();
    }
}
