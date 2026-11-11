package io.materializer.pipelinestructureplanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.pipelinestructureplanner.models.RepositoryPush;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.shared.utilities.ResourceUtility;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushHistoryFetcherTest {

    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);
    private static final String REPO = "owner/example";
    private final GitHub gitHub = mock(GitHub.class);
    private final HttpClient httpClient = mock(HttpClient.class);
    private final PushHistoryFetcher pushHistoryFetcher = new PushHistoryFetcher(
            gitHub,
            httpClient,
            new EnvironmentVariables("test", REPO, REPO)
    );

    @Test
    void readsCapturedGitHubActivityFields() throws IOException {
        // Captured from GitHub's pure-materializer activity endpoint, not generated from our model.
        String json = ResourceUtility.readString("/pipelinestructureplanner/repository-activity.json");
        RepositoryPush[] pushes = JsonMapperUtility.MAPPER.readValue(json, RepositoryPush[].class);

        RepositoryPush expected = new RepositoryPush(
                49784801818L,
                "d229185fce08f9fefd8c3ae17c1b421eaeb37f67",
                "744fb8afe3e7d97de93bf982efad2db10fef9133",
                Instant.parse("2026-11-10T23:39:46Z")
        );
        assertEquals(List.of(expected), List.of(pushes));
    }

    @Test
    void propagatesRepositoryLookupFailures() throws IOException {
        IOException failure = new IOException("Repository unavailable");
        when(gitHub.getRepository(REPO)).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> pushHistoryFetcher.latestOnDefaultBranch(REPO)
        );

        assertSame(failure, exception.getCause());
    }

    @Test
    void takesSHAAndIDFromOnePushOnTheDefaultBranch() throws IOException, InterruptedException {
        GHRepository repository = mock(GHRepository.class);
        when(gitHub.getRepository(REPO)).thenReturn(repository);
        when(repository.getDefaultBranch()).thenReturn("trunk");
        doReturn(page(push(10, B, A), Map.of())).when(httpClient).send(any(), any());

        assertEquals(new RepositorySnapshot(REPO, "trunk", A, 10), pushHistoryFetcher.latestOnDefaultBranch(REPO));
    }

    @Test
    void readsPagesUntilTheRecordedPushEvenWhenTheSHAReturnsToItsOldValue() throws IOException, InterruptedException {
        Map<String, List<String>> links = Map.of("Link", List.of("<https://api.github.com/page2>; rel=\"next\""));
        HttpResponse<?> first = page(push(12, B, A), links);
        HttpResponse<?> second = page(push(11, A, B) + "," + push(10, B, A), Map.of());
        doReturn(first, second).when(httpClient).send(any(), any());

        List<RepositoryPush> pushes = pushHistoryFetcher.since(new RepositorySnapshot(REPO, "main", A, 10));

        assertEquals(List.of(11L, 12L), pushes.stream().map(RepositoryPush::pushID).toList());
    }

    @Test
    void rejectsMissingCheckpoints() throws IOException, InterruptedException {
        doReturn(page(push(12, B, A), Map.of())).when(httpClient).send(any(), any());

        RepositorySnapshot baseSnapshot = new RepositorySnapshot(REPO, "main", A, 10);
        assertThrows(IllegalStateException.class, () -> pushHistoryFetcher.since(baseSnapshot));
    }

    private String push(long id, String before, String after) {
        return """
                {"id":%d,"before":"%s","after":"%s","timestamp":"2026-11-07T01:00:00Z"}
                """.strip().formatted(id, before, after);
    }

    private HttpResponse<String> page(String items, Map<String, List<String>> links) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[" + items + "]");
        when(response.headers()).thenReturn(HttpHeaders.of(links, (name, value) -> true));
        return response;
    }
}
