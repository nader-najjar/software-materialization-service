package io.materializer.pipelinestructureplanner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.pipelinestructureplanner.models.MaterializerRegistration;
import io.materializer.pipelinestructureplanner.models.MaterializerRegistry;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterializerRegistryFetcherTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHContent content = mock(GHContent.class);
    private static final String REGISTRY_REPOSITORY = "owner/registry";

    private final MaterializerRegistryFetcher materializerRegistryFetcher = new MaterializerRegistryFetcher(
            gitHub,
            new EnvironmentVariables(
                    "test", REGISTRY_REPOSITORY, "owner/storage", "owner/service"
            )
    );

    @Test
    void readsTheRegistryFromTheDefaultBranchAndClosesItsStream() throws IOException {
        InputStream stream = stubRegistry("{\"registrations\":[{\"repositoryName\":\"owner/pipeline\"}]}");
        MaterializerRegistry registry = materializerRegistryFetcher.fetch();

        assertEquals(new MaterializerRegistration("owner/pipeline"), registry.registrations().getFirst());
        verify(repository).getFileContent("materializer-registry.json");

        verify(stream).close();
    }

    @Test
    void rejectsInvalidRegistryContent() throws IOException {
        InputStream stream = stubRegistry("{\"registrations\":null}");

        assertThrows(IllegalArgumentException.class, () -> materializerRegistryFetcher.fetch());
        verify(stream).close();
    }

    @Test
    void preservesGitHubFailuresWithRepositoryContext() throws IOException {
        IOException failure = new IOException("Access denied");
        when(gitHub.getRepository(REGISTRY_REPOSITORY)).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> materializerRegistryFetcher.fetch()
        );

        assertEquals(
                "Could not read materializer registry from " + REGISTRY_REPOSITORY,
                exception.getMessage()
        );

        assertSame(failure, exception.getCause());
    }

    private InputStream stubRegistry(String json) throws IOException {
        InputStream stream = spy(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(gitHub.getRepository(REGISTRY_REPOSITORY)).thenReturn(repository);
        when(repository.getFileContent("materializer-registry.json")).thenReturn(content);

        when(content.read()).thenReturn(stream);
        return stream;
    }
}
