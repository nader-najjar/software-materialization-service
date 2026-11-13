package io.materializer.shared.materializerregistrystore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.materializer.environment.EnvironmentVariables;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import io.materializer.shared.utilities.JsonMapperUtility;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHContentBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterializerRegistryStoreTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHContent content = mock(GHContent.class);
    private static final String REGISTRY_REPOSITORY = "owner/registry";

    private final MaterializerRegistryStore materializerRegistryStore = new MaterializerRegistryStore(
            gitHub,
            new EnvironmentVariables(
                    "test",
                    REGISTRY_REPOSITORY,
                    "owner/inputs",
                    null,
                    null
            )
    );

    @Test
    void readsTheRegistryFromTheDefaultBranchAndClosesItsStream() throws IOException {
        InputStream stream = stubRegistry("{\"registrations\":[{\"repositoryName\":\"owner/pipeline\"}]}");
        MaterializerRegistry registry = materializerRegistryStore.read();

        assertEquals(new MaterializerRegistration("owner/pipeline"), registry.registrations().getFirst());
        verify(repository).getFileContent("materializer-registry.json");

        verify(stream).close();
    }

    @Test
    void rejectsInvalidRegistryContent() throws IOException {
        InputStream stream = stubRegistry("{\"registrations\":null}");

        assertThrows(IllegalArgumentException.class, () -> materializerRegistryStore.read());
        verify(stream).close();
    }

    @Test
    void preservesGitHubFailuresWithRepositoryContext() throws IOException {
        IOException failure = new IOException("Access denied");
        when(gitHub.getRepository(REGISTRY_REPOSITORY)).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> materializerRegistryStore.read()
        );

        assertEquals(
                "Could not read materializer registry from " + REGISTRY_REPOSITORY,
                exception.getMessage()
        );

        assertSame(failure, exception.getCause());
    }

    @Test
    void preservesContentReadFailuresWithRepositoryContext() throws IOException {
        stubRegistry("{\"registrations\":[]}");
        IOException failure = new IOException("Content unavailable");
        when(content.read()).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> materializerRegistryStore.read()
        );

        assertEquals("Could not read materializer registry from " + REGISTRY_REPOSITORY, exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    @Test
    void writesUsingTheOriginalFileRevisionWithoutReadingAgain() throws IOException {
        stubRegistry("{\"registrations\":[{\"repositoryName\":\"owner/other\"}]}");
        when(content.getSha()).thenReturn("original-file-sha");
        MaterializerRegistrySnapshot snapshot = materializerRegistryStore.readSnapshot();
        GHContentBuilder writer = mock(GHContentBuilder.class, RETURNS_SELF);
        when(repository.createContent()).thenReturn(writer);

        materializerRegistryStore.addRegistration(snapshot, "owner/new");

        verify(repository).getFileContent("materializer-registry.json");
        verify(writer).sha("original-file-sha");
        verify(writer).path("materializer-registry.json");
        verify(writer).message("Register owner/new");
        verify(writer).commit();
        assertUpdatedRegistrations(writer);
    }

    private void assertUpdatedRegistrations(GHContentBuilder writer) throws IOException {
        ArgumentCaptor<byte[]> updated = ArgumentCaptor.forClass(byte[].class);
        verify(writer).content(updated.capture());
        MaterializerRegistry registry = JsonMapperUtility.MAPPER.readValue(
                updated.getValue(),
                MaterializerRegistry.class
        );
        assertEquals(2, registry.registrations().size());
        assertEquals("owner/other", registry.registrations().getFirst().repositoryName());
        assertEquals("owner/new", registry.registrations().getLast().repositoryName());
    }

    private InputStream stubRegistry(String json) throws IOException {
        InputStream stream = spy(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(gitHub.getRepository(REGISTRY_REPOSITORY)).thenReturn(repository);
        when(repository.getFileContent("materializer-registry.json")).thenReturn(content);

        when(content.read()).thenReturn(stream);
        return stream;
    }
}
