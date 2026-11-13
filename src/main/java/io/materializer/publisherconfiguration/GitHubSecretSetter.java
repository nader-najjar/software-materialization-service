package io.materializer.publisherconfiguration;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.inject.Inject;
import io.materializer.environment.EnvironmentVariables;

public class GitHubSecretSetter {

    private final EnvironmentVariables environmentVariables;

    @Inject
    public GitHubSecretSetter(EnvironmentVariables environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public void setBuildPublisherPrivateKey(String repositoryName, String privateKey) {
        try {
            setSecret(repositoryName, privateKey);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting Build Publisher private key");

        } catch (IOException exception) {
            throw new IllegalStateException("Could not set Build Publisher private key with GitHub CLI");
        }
    }

    private void setSecret(
            String repositoryName,
            String privateKey
    ) throws IOException, InterruptedException {
        Path configurationDirectory = Files.createTempDirectory("materializer-gh-");
        try {
            ProcessBuilder builder = createProcessBuilder(repositoryName, configurationDirectory);
            run(builder.start(), privateKey);

        } finally {
            Files.deleteIfExists(configurationDirectory);
        }
    }

    private ProcessBuilder createProcessBuilder(String repositoryName, Path configurationDirectory) {
        ProcessBuilder builder = new ProcessBuilder(
                "gh",
                "secret",
                "set",
                "BUILD_PUBLISHER_APP_PRIVATE_KEY",
                "--repo",
                "github.com/" + repositoryName,
                "--app",
                "actions"
        );
        configureEnvironment(builder.environment(), configurationDirectory);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        return builder;
    }

    private void configureEnvironment(Map<String, String> environment, Path configurationDirectory) {
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) {
            environment.put("PATH", path);
        }
        environment.put("GH_TOKEN", environmentVariables.githubToken());
        environment.put("GH_HOST", "github.com");
        environment.put("GH_PROMPT_DISABLED", "1");
        environment.put("GH_NO_UPDATE_NOTIFIER", "1");
        environment.put("GH_NO_EXTENSION_UPDATE_NOTIFIER", "1");
        environment.put("GH_CONFIG_DIR", configurationDirectory.toString());
        environment.put("HOME", configurationDirectory.toString());
    }

    private void run(Process process, String privateKey) throws IOException, InterruptedException {
        int exitCode;
        try {
            exitCode = writeSecretAndWait(process, privateKey);

        } finally {
            process.destroyForcibly();
        }
        if (exitCode != 0) {
            throw new IllegalStateException("GitHub CLI secret setting failed with exit code " + exitCode);
        }
    }

    private int writeSecretAndWait(Process process, String privateKey) throws IOException, InterruptedException {
        try (OutputStream input = process.getOutputStream()) {
            input.write(privateKey.getBytes(StandardCharsets.UTF_8));
        }
        return process.waitFor();
    }
}
