package io.materializer.environment;

import java.util.Map;

import io.materializer.environment.exceptions.EnvironmentVariableException;
import io.materializer.environment.models.EnvironmentVariables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentVariablesFactoryTest {

    @Test
    void loadsTokenWithoutExposingItInTheModelDescription() {
        EnvironmentVariables environment = EnvironmentVariablesFactory.from(Map.of(
                "GITHUB_TOKEN", "example-token",
                "MATERIALIZER_REGISTRY_REPOSITORY", "owner/registry",
                "PIPELINE_STRUCTURE_STORE_REPOSITORY", "owner/storage"
        ));

        assertEquals("example-token", environment.githubToken());
        assertEquals("owner/registry", environment.materializerRegistryRepository());
        assertEquals("owner/storage", environment.pipelineStructureStoreRepository());
        assertFalse(environment.toString().contains("example-token"));
    }

    @Test
    void rejectsInvalidStorageRepository() {
        Map<String, String> environment = Map.of(
                "GITHUB_TOKEN", "example-token",
                "MATERIALIZER_REGISTRY_REPOSITORY", "owner/registry",
                "PIPELINE_STRUCTURE_STORE_REPOSITORY", "invalid"
        );

        assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.from(environment));
    }

    @Test
    void rejectsMissingToken() {
        EnvironmentVariableException exception = assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.from(Map.of())
        );

        assertTrue(exception.getMessage().contains("GITHUB_TOKEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankTokens(String token) {
        assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.from(Map.of("GITHUB_TOKEN", token))
        );
    }
}
