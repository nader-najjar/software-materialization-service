package io.materializer.pipelinestructureplanner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.materializer.shared.models.RepositorySnapshot;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeclaredPipelineInputsFetcherTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final DeclaredPipelineInputsFetcher declaredPipelineInputsFetcher =
            new DeclaredPipelineInputsFetcher(gitHub);
    private final RepositorySnapshot materializerSnapshot = new RepositorySnapshot(
            "owner/materializer",
            "main",
            "a".repeat(40),
            42
    );

    @Test
    void closesPipelineFileWhenInputsAreInvalid() throws IOException {
        GHRepository repository = mock(GHRepository.class);
        GHContent pipelineFile = mock(GHContent.class);
        InputStream content = spy(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        when(gitHub.getRepository(materializerSnapshot.repositoryName())).thenReturn(repository);
        when(repository.getFileContent("pipeline.json", materializerSnapshot.commitSHA())).thenReturn(pipelineFile);
        when(pipelineFile.read()).thenReturn(content);

        assertThrows(IllegalArgumentException.class, () -> declaredPipelineInputsFetcher.fetch(materializerSnapshot));
        verify(content).close();
    }

    @Test
    void preservesRepositoryReadFailure() throws IOException {
        IOException failure = new IOException("Repository unavailable");
        when(gitHub.getRepository(materializerSnapshot.repositoryName())).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> declaredPipelineInputsFetcher.fetch(materializerSnapshot)
        );

        assertSame(failure, exception.getCause());
    }
}
