package io.materializer.shared.materializerregistrystore.models;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.shared.utilities.JsonSanitizer;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterializerRegistryValidationTest {

    @Test
    void readsDistinctRegistrationsInOrder() {
        String json = """
                {"registrations":[{"repositoryName":"owner/first"},{"repositoryName":"owner/second"}]}
                """;

        MaterializerRegistry registry = JsonSanitizer.parseAndValidate(json, MaterializerRegistry.class);

        assertEquals(
                List.of("owner/first", "owner/second"),
                registry.registrations().stream().map(registration -> registration.repositoryName()).toList()
        );
    }

    @Test
    void permitsAnEmptyRegistry() {
        MaterializerRegistry registry = JsonSanitizer.parseAndValidate(
                "{\"registrations\":[]}",
                MaterializerRegistry.class
        );

        assertTrue(registry.registrations().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{", "[]", "{\"registrations\":[]} {}", "{\"registrations\":[],\"registrations\":[]}"
    })
    void rejectsMalformedDuplicateAndTrailingJSON(String json) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, MaterializerRegistry.class)
        );

        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "{}",
        "{\"registrations\":[{}]}",
        "{\"registrations\":null}",
        "{\"registrations\":[null]}",
        "{\"registrations\":[{\"repositoryName\":null}]}",
        "{\"registrations\":[{\"repositoryName\":\"invalid\"}]}"
    })
    void validatesTheRegistryAndItsNestedEntries(String json) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, MaterializerRegistry.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner/repo", "OWNER/REPO"})
    void rejectsDuplicateRepositoriesRegardlessOfCase(String repository) {
        String json = """
                {"registrations":[{"repositoryName":"owner/repo"},{"repositoryName":"%s"}]}\
                """.formatted(repository);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, MaterializerRegistry.class)
        );

        String message = "registrations: Materializer repositories must be unique regardless of case";
        assertTrue(exception.getMessage().contains(message));
    }
}
