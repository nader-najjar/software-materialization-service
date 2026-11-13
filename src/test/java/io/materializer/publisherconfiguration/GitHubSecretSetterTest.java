package io.materializer.publisherconfiguration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.materializer.environment.EnvironmentVariables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubSecretSetterTest {

    private final EnvironmentVariables environment = new EnvironmentVariables(
            "test-token",
            null,
            null,
            null,
            null
    );
    private final Process process = mock(Process.class);
    private final GitHubSecretSetter setter = new GitHubSecretSetter(environment);
    private MockedConstruction<ProcessBuilder> builders;
    private IOException startFailure;

    @BeforeEach
    void interceptProcessCreation() {
        builders = mockConstruction(ProcessBuilder.class, this::prepareBuilder);
    }

    private void prepareBuilder(ProcessBuilder builder, MockedConstruction.Context context) throws IOException {
        Map<String, String> processEnvironment = new HashMap<>(Map.of("PATH", "/test/bin", "GH_DEBUG", "api"));
        when(builder.environment()).thenReturn(processEnvironment);
        when(builder.command()).thenReturn(List.of((String[]) context.arguments().getFirst()));
        if (startFailure == null) {
            when(builder.start()).thenReturn(process);

        } else {
            when(builder.start()).thenThrow(startFailure);
        }
    }

    @AfterEach
    void closeProcessInterception() {
        builders.close();
    }

    @Test
    void passesTheKeyOnlyThroughUtf8StdinWithIsolatedConfiguration() throws InterruptedException {
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        when(process.getOutputStream()).thenReturn(stdin);
        when(process.waitFor()).thenReturn(0);

        setter.setBuildPublisherPrivateKey("owner/repository", "test-key\né\n");

        assertEquals("test-key\né\n", stdin.toString(StandardCharsets.UTF_8));
        assertCommandAndEnvironment(builders.constructed().getFirst());
        assertFalse(Files.exists(Path.of(builders.constructed().getFirst().environment().get("GH_CONFIG_DIR"))));
        verify(process).destroyForcibly();
    }

    private void assertCommandAndEnvironment(ProcessBuilder builder) {
        List<String> command = List.of(
                "gh",
                "secret",
                "set",
                "BUILD_PUBLISHER_APP_PRIVATE_KEY",
                "--repo",
                "github.com/owner/repository",
                "--app",
                "actions"
        );
        assertEquals(command, builder.command());
        assertEnvironment(builder);
    }

    private void assertEnvironment(ProcessBuilder builder) {
        assertEquals("test-token", builder.environment().get("GH_TOKEN"));
        assertEquals(builder.environment().get("HOME"), builder.environment().get("GH_CONFIG_DIR"));
        Set<String> allowedVariables = Set.of(
                "PATH",
                "GH_TOKEN",
                "GH_HOST",
                "GH_PROMPT_DISABLED",
                "GH_NO_UPDATE_NOTIFIER",
                "GH_NO_EXTENSION_UPDATE_NOTIFIER",
                "GH_CONFIG_DIR",
                "HOME"
        );
        assertTrue(allowedVariables.containsAll(builder.environment().keySet()));
        verify(builder).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        verify(builder).redirectError(ProcessBuilder.Redirect.DISCARD);
    }

    @Test
    void failsSafelyOnNonzeroExit() throws InterruptedException {
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.waitFor()).thenReturn(1);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> setter.setBuildPublisherPrivateKey("owner/repository", "test-key")
        );

        assertEquals("GitHub CLI secret setting failed with exit code 1", failure.getMessage());
        verify(process).destroyForcibly();
    }

    @Test
    void doesNotExposeProcessStartErrors() {
        startFailure = new IOException("sensitive diagnostics");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> setter.setBuildPublisherPrivateKey("owner/repository", "test-key")
        );

        assertEquals("Could not set Build Publisher private key with GitHub CLI", failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void stopsTheProcessIfWritingTheSecretFails() throws IOException {
        OutputStream stdin = mock(OutputStream.class);
        when(process.getOutputStream()).thenReturn(stdin);
        doThrow(new IOException("sensitive diagnostics")).when(stdin).write(any(byte[].class));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> setter.setBuildPublisherPrivateKey("owner/repository", "test-key")
        );

        assertEquals("Could not set Build Publisher private key with GitHub CLI", failure.getMessage());
        assertNull(failure.getCause());
        verify(process).destroyForcibly();
    }

    @Test
    void restoresInterruptionAndStopsTheProcess() throws InterruptedException {
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.waitFor()).thenThrow(new InterruptedException());
        try {
            assertInterruptedCall();

        } finally {
            Thread.interrupted();
        }
        verify(process).destroyForcibly();
    }

    private void assertInterruptedCall() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> setter.setBuildPublisherPrivateKey("owner/repository", "test-key")
        );
        assertEquals("Interrupted while setting Build Publisher private key", failure.getMessage());
        assertTrue(Thread.currentThread().isInterrupted());
    }

}
