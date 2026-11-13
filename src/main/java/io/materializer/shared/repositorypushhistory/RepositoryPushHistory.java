package io.materializer.shared.repositorypushhistory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.EnvironmentVariables;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.models.RepositoryPush;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonSanitizer;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class RepositoryPushHistory {

    private static final Pattern NEXT_PAGE = Pattern.compile("<([^>]+)>; rel=\"next\"");
    private final GitHub gitHub;
    private final HttpClient httpClient;
    private final String githubToken;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public RepositoryPushHistory(GitHub gitHub, HttpClient httpClient, EnvironmentVariables environmentVariables) {
        this.gitHub = gitHub;
        this.httpClient = httpClient;
        this.githubToken = environmentVariables.githubToken();
    }

    public RepositorySnapshot latestOnDefaultBranch(String repositoryName) {
        String defaultBranch = getDefaultBranch(repositoryName);
        return latest(repositoryName, defaultBranch);
    }

    public RepositorySnapshot latest(String repositoryName, String branch) {
        Page page = fetch(activityURI(repositoryName, branch, 1));
        if (page.pushes().isEmpty()) {
            throw new IllegalStateException("Missing push history for %s in %s".formatted(branch, repositoryName));
        }

        RepositoryPush push = page.pushes().getFirst();
        if (push.deletesBranch()) {
            String message = "Branch %s in %s was deleted".formatted(branch, repositoryName);
            throw new IllegalStateException(message);
        }

        return new RepositorySnapshot(repositoryName, branch, push.after(), push.pushID());
    }

    public List<RepositoryPush> since(RepositorySnapshot baseSnapshot) {
        List<RepositoryPush> pushes = new ArrayList<>();
        URI uri = activityURI(baseSnapshot.repositoryName(), baseSnapshot.branch(), 100);
        while (uri != null) {
            Page page = fetch(uri);
            if (collectUntilCheckpoint(page.pushes(), baseSnapshot, pushes)) { return pushes.reversed(); }
            uri = page.next();
        }

        String message = "Missing push %d in %s".formatted(baseSnapshot.pushID(), baseSnapshot.repositoryName());
        throw new IllegalStateException(message);
    }

    private String getDefaultBranch(String repositoryName) {
        try {
            GHRepository repository = gitHub.getRepository(repositoryName);
            return repository.getDefaultBranch();

        } catch (IOException exception) {
            String message = "Could not resolve default branch in %s".formatted(repositoryName);
            throw new UncheckedIOException(message, exception);
        }
    }

    private URI activityURI(String repositoryName, String branch, int pageSize) {
        String ref = URLEncoder.encode("refs/heads/" + branch, StandardCharsets.UTF_8);
        return URI.create("https://api.github.com/repos/%s/activity?ref=%s&direction=desc&per_page=%d"
                .formatted(repositoryName, ref, pageSize));
    }

    private Page fetch(URI uri) {
        if (!"https".equals(uri.getScheme()) || !"api.github.com".equals(uri.getRawAuthority())) {
            throw new IllegalStateException("Unexpected repository activity URL");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parsePage(response);

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read push history at " + uri, exception);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading push history", exception);
        }
    }

    private boolean collectUntilCheckpoint(List<RepositoryPush> page, RepositorySnapshot baseSnapshot,
            List<RepositoryPush> pushes) {
        for (RepositoryPush push : page) {
            if (!pushes.isEmpty() && !pushes.getLast().before().equals(push.after())) {
                throw new IllegalStateException("Incomplete push history for " + baseSnapshot.repositoryName());
            }
            if (push.pushID().equals(baseSnapshot.pushID())) {
                verifyCheckpoint(push, baseSnapshot);
                return true;
            }
            pushes.add(push);
        }

        return false;
    }

    private Page parsePage(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException("Repository activity request returned HTTP " + response.statusCode());
        }

        RepositoryPush[] pushes = JsonSanitizer.parseAndValidate(response.body(), RepositoryPush[].class);
        for (RepositoryPush push : pushes) {
            HibernateValidatorUtility.validate(push);
            Instant.parse(push.pushedAt());
        }
        Matcher next = NEXT_PAGE.matcher(String.join(",", response.headers().allValues("Link")));
        return new Page(List.of(pushes), next.find() ? URI.create(next.group(1)) : null);
    }

    private void verifyCheckpoint(RepositoryPush push, RepositorySnapshot baseSnapshot) {
        if (!baseSnapshot.commitSHA().equals(push.after())) {
            String message = "Snapshot SHA differs from recorded push in " + baseSnapshot.repositoryName();
            throw new IllegalStateException(message);
        }
    }

    private record Page(List<RepositoryPush> pushes, URI next) { }
}
