package io.materializer.execution.models;

import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.shared.utilities.JsonSanitizer;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationInputTest {

    @Test
    void readsRegistrationInput() {
        ApplicationInput input = JsonSanitizer.parseAndValidate(
                "{\"operation\":\"register\",\"repositoryName\":\"owner/materializer\"}",
                ApplicationInput.class
        );

        assertEquals("register", input.operation());
        assertEquals("owner/materializer", input.repositoryName());
        assertEquals(2, JsonMapperUtility.MAPPER.valueToTree(input).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"operation\":\"materialize-new-pushes\"}",
        "{\"operation\":\"materialize-new-pushes\",\"repositoryName\":null}"
    })
    void materializationDoesNotRequireARepository(String json) {
        ApplicationInput input = JsonSanitizer.parseAndValidate(json, ApplicationInput.class);

        assertEquals("materialize-new-pushes", input.operation());
        assertNull(input.repositoryName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"operation\":\"register\"}",
        "{\"operation\":\"register\",\"repositoryName\":null}",
        "{\"operation\":\"register\",\"repositoryName\":\"\"}",
        "{\"operation\":\"register\",\"repositoryName\":\" \"}",
        "{\"operation\":\"register\",\"repositoryName\":\"repo\"}",
        "{\"operation\":\"register\",\"repositoryName\":\"owner/\"}",
        "{\"operation\":\"register\",\"repositoryName\":\"/repo\"}",
        "{\"operation\":\"register\",\"repositoryName\":\"owner/repo/extra\"}"
    })
    void registrationRequiresAnOwnerAndRepository(String json) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, ApplicationInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "123", "[]", "{}"})
    void rejectsNonStringRepositoryNames(String repositoryName) {
        String json = "{\"operation\":\"register\",\"repositoryName\":%s}".formatted(repositoryName);

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, ApplicationInput.class)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\" \"", "\"unknown\"", "\"REGISTER\""})
    void rejectsInvalidOperations(String operation) {
        String json = "{\"operation\":%s,\"repositoryName\":\"owner/materializer\"}".formatted(operation);

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, ApplicationInput.class)
        );
    }

}
