package io.materializer.execution;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.materializer.environment.EnvironmentVariables;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.publisherconfiguration.GitHubSecretSetter;
import io.materializer.publisherconfiguration.PublisherConfigurator;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHContentBuilder;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryVariable;
import org.kohsuke.github.GitHub;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutorRegistrationIntegrationTest {

    private static final String TARGET = "owner/materializer";
    private static final String VARIABLE = "BUILD_PUBLISHER_APP_CLIENT_ID";
    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository registryRepository = mock(GHRepository.class);
    private final GHRepository targetRepository = mock(GHRepository.class);
    private final GHContent registryContent = mock(GHContent.class);
    private final GHContentBuilder registryWriter = mock(GHContentBuilder.class, RETURNS_SELF);
    private final GHRepositoryVariable variable = mock(GHRepositoryVariable.class);
    private final GHRepositoryVariable.Setter variableSetter = mock(GHRepositoryVariable.Setter.class);
    private final GitHubSecretSetter secretSetter = mock(GitHubSecretSetter.class);
    private final EnvironmentVariables environment = new EnvironmentVariables(
            "test-token",
            "owner/service",
            null,
            "test-client-id",
            "test-private-key"
    );
    private final Executor executor = new Executor(
            new RegisterExecutor(
                    environment,
                    new MaterializerRegistryStore(gitHub, environment),
                    new PublisherConfigurator(gitHub, secretSetter, environment)
            ),
            new MaterializeNewPushesExecutor(
                    mock(LivePipelineInputsAdvancer.class),
                    mock(LivePipelineInputsReconciler.class),
                    mock(WorkflowGenerator.class),
                    mock(WorkflowPublisher.class),
                    environment,
                    new MaterializerRegistryStore(gitHub, environment)
            )
    );

    private void register() {
        executor.execute(new String[] {"{\"operation\":\"register\",\"repositoryName\":\"owner/materializer\"}"});
    }

    @BeforeEach
    void prepareRegistry() throws IOException {
        when(gitHub.getRepository("owner/service")).thenReturn(registryRepository);
        when(registryRepository.getFileContent("materializer-registry.json")).thenReturn(registryContent);
        when(registryRepository.createContent()).thenReturn(registryWriter);
        when(registryContent.getSha()).thenReturn("original-file-sha");
        when(gitHub.getRepository(TARGET)).thenReturn(targetRepository);
        when(targetRepository.getVariable(VARIABLE)).thenReturn(variable);
        when(variable.set()).thenReturn(variableSetter);
        registry("{\"registrations\":[{\"repositoryName\":\"owner/existing\"}]}");
    }

    @Test
    void updatesPublisherCredentialsBeforeAppendingToTheSameRegistryContent() throws IOException {
        register();

        InOrder order = inOrder(variableSetter, secretSetter, registryWriter);
        order.verify(variableSetter).value("test-client-id");
        order.verify(secretSetter).setBuildPublisherPrivateKey(TARGET, "test-private-key");
        ArgumentCaptor<byte[]> json = ArgumentCaptor.forClass(byte[].class);
        order.verify(registryWriter).content(json.capture());
        order.verify(registryWriter).message("Register " + TARGET);
        order.verify(registryWriter).commit();
        verify(registryWriter).sha("original-file-sha");
        verify(registryRepository).getFileContent("materializer-registry.json");
        MaterializerRegistry updated = JsonMapperUtility.MAPPER.readValue(json.getValue(), MaterializerRegistry.class);
        assertEquals(
                List.of(new MaterializerRegistration("owner/existing"), new MaterializerRegistration(TARGET)),
                updated.registrations()
        );
    }

    @Test
    void createsThePublisherVariableIfItDoesNotExist() throws IOException {
        when(targetRepository.getVariable(VARIABLE)).thenThrow(new GHFileNotFoundException());

        register();

        InOrder order = inOrder(targetRepository, secretSetter, registryWriter);
        order.verify(targetRepository).createVariable(VARIABLE, "test-client-id");
        order.verify(secretSetter).setBuildPublisherPrivateKey(TARGET, "test-private-key");
        order.verify(registryWriter).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner/materializer", "OWNER/MATERIALIZER"})
    void rejectsDuplicatesBeforeAccessingTheTarget(String registered) throws IOException {
        registry("{\"registrations\":[{\"repositoryName\":\"%s\"}]}".formatted(registered));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> register()
        );

        assertEquals("Materializer is already registered: " + TARGET, exception.getMessage());
        verify(gitHub, never()).getRepository(TARGET);
        verifyNoInteractions(secretSetter, targetRepository);
        verify(registryWriter, never()).commit();
    }

    @Test
    void rejectsAnInvalidRegistryBeforeConfiguringCredentials() throws IOException {
        registry("{\"registrations\":null}");

        assertThrows(IllegalArgumentException.class, () -> register());

        verifyNoInteractions(secretSetter, targetRepository);
        verify(registryWriter, never()).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"target", "read-variable", "create-variable", "update-variable"})
    void stopsBeforeSecretOrRegistryWritesWhenVariableSetupFails(String stage) throws IOException {
        IOException failure = new IOException("Access denied");
        failVariableSetup(stage, failure);

        UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> register());

        assertSame(failure, exception.getCause());
        verifyNoInteractions(secretSetter);
        verify(registryWriter, never()).commit();
    }

    @Test
    void doesNotWriteRegistryIfSecretSetupFails() throws IOException {
        IllegalStateException failure = new IllegalStateException("gh failed");
        doThrow(failure).when(secretSetter).setBuildPublisherPrivateKey(TARGET, "test-private-key");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> register()));

        verify(registryWriter, never()).commit();
    }

    @Test
    void reportsRegistryWriteFailureWithoutRetryingProvisioning() throws IOException {
        IOException conflict = new IOException("409 SHA conflict");
        when(registryWriter.commit()).thenThrow(conflict);

        UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> register());

        assertSame(conflict, exception.getCause());
        assertEquals(
                "Could not update materializer registry in owner/service",
                exception.getMessage()
        );
        verify(secretSetter).setBuildPublisherPrivateKey(TARGET, "test-private-key");
        verify(registryWriter).commit();
        verify(registryWriter).sha("original-file-sha");
    }

    private void failVariableSetup(String stage, IOException failure) throws IOException {
        switch (stage) {
            case "target" -> when(gitHub.getRepository(TARGET)).thenThrow(failure);
            case "read-variable" -> when(targetRepository.getVariable(VARIABLE)).thenThrow(failure);
            case "update-variable" -> when(variableSetter.value("test-client-id")).thenThrow(failure);
            case "create-variable" -> failVariableCreation(failure);
            default -> throw new IllegalArgumentException(stage);
        }
    }

    private void failVariableCreation(IOException failure) throws IOException {
        when(targetRepository.getVariable(VARIABLE)).thenThrow(new GHFileNotFoundException());
        doThrow(failure).when(targetRepository).createVariable(VARIABLE, "test-client-id");
    }

    private void registry(String json) throws IOException {
        when(registryContent.read()).thenAnswer(invocation ->
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

}
