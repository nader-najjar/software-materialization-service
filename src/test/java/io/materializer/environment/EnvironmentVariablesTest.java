package io.materializer.environment;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentVariablesTest {

    @Test
    void mapsExplicitEnvironmentNamesToStrings() {
        EnvironmentVariables environment = JsonMapperUtility.MAPPER.convertValue(Map.of(
                "GITHUB_TOKEN", "example-token",
                "MATERIALIZER_REGISTRY_REPOSITORY", "owner/registry",
                "LIVE_PIPELINE_INPUTS_STORE_REPOSITORY", "owner/inputs",
                "BUILD_PUBLISHER_APP_CLIENT_ID", "example-client-id",
                "BUILD_PUBLISHER_APP_PRIVATE_KEY", "example\nprivate\nkey"
        ), EnvironmentVariables.class);

        assertEquals("example-token", environment.githubToken());
        assertEquals("owner/registry", environment.materializerRegistryRepository());
        assertEquals("owner/inputs", environment.livePipelineInputsStoreRepository());
        assertEquals("example-client-id", environment.buildPublisherAppClientID());
        assertEquals("example\nprivate\nkey", environment.buildPublisherAppPrivateKey());
    }

    @Test
    void preservesMissingValuesAsNullAndIgnoresUnrelatedEnvironmentKeys() {
        EnvironmentVariables environment = JsonMapperUtility.MAPPER.convertValue(Map.of(
                "PATH", "/usr/bin", "HOME", "/home/test", "githubToken", "not-the-environment-name"
        ), EnvironmentVariables.class);
        assertNull(environment.githubToken());
        assertNull(environment.materializerRegistryRepository());
        assertNull(environment.livePipelineInputsStoreRepository());
        assertNull(environment.buildPublisherAppClientID());
        assertNull(environment.buildPublisherAppPrivateKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", " value ", "true", "42", "NOT_NEEDED_FOR_OPERATION"})
    void preservesValuesWithoutParsingOrValidation(String value) {
        EnvironmentVariables environment = JsonMapperUtility.MAPPER.convertValue(Map.of(
                "GITHUB_TOKEN", value,
                "MATERIALIZER_REGISTRY_REPOSITORY", value,
                "LIVE_PIPELINE_INPUTS_STORE_REPOSITORY", value,
                "BUILD_PUBLISHER_APP_CLIENT_ID", value,
                "BUILD_PUBLISHER_APP_PRIVATE_KEY", value
        ), EnvironmentVariables.class);
        assertEquals(value, environment.githubToken());
        assertEquals(value, environment.materializerRegistryRepository());
        assertEquals(value, environment.livePipelineInputsStoreRepository());
        assertEquals(value, environment.buildPublisherAppClientID());
        assertEquals(value, environment.buildPublisherAppPrivateKey());
    }

    @Test
    void doesNotExposeSecretsInDescriptionsOrSerializedOutput() throws JsonProcessingException {
        EnvironmentVariables environment = JsonMapperUtility.MAPPER.convertValue(Map.of(
                "GITHUB_TOKEN", "example-token",
                "BUILD_PUBLISHER_APP_PRIVATE_KEY", "example-private-key"
        ), EnvironmentVariables.class);
        assertFalse(environment.toString().contains("example-token"));
        assertFalse(environment.toString().contains("example-private-key"));
        String json = JsonMapperUtility.MAPPER.writeValueAsString(environment);
        assertFalse(json.contains("example-token"));
        assertFalse(json.contains("example-private-key"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "githubToken", "materializerRegistryRepository", "livePipelineInputsStoreRepository",
        "buildPublisherAppClientID", "buildPublisherAppPrivateKey"
    })
    void declaresBothNullAndBlankConstraintsForEachRequiredProperty(String property) {
        EnvironmentVariables environment = JsonMapperUtility.MAPPER.convertValue(Map.of(), EnvironmentVariables.class);
        var constraintTypes = HibernateValidatorUtility.VALIDATOR.validateProperty(environment, property).stream()
                .map(violation -> violation.getConstraintDescriptor().getAnnotation().annotationType())
                .collect(Collectors.toSet());
        assertEquals(Set.of(NotNull.class, NotBlank.class), constraintTypes);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingOrBlankTokenWhenValidatingStartupConfiguration(String token) {
        EnvironmentVariables environment = new EnvironmentVariables(token, null, null, null, null);
        assertThrows(ValidationException.class,
                () -> HibernateValidatorUtility.validateProperties(environment, "githubToken"));
    }

    @Test
    void startupValidationDoesNotRequireOperationSpecificValues() {
        EnvironmentVariables environment = new EnvironmentVariables("test-token", null, null, null, null);
        assertDoesNotThrow(() -> HibernateValidatorUtility.validateProperties(environment, "githubToken"));
    }
}
