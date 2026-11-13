package io.materializer.shared.utilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.execution.models.ApplicationInput;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonSanitizerTest {

    private static final String INPUT_JSON = "{\"operation\":%s}";

    private static final String VALID_OPERATION = "\"materialize-new-pushes\"";

    @Test
    void rejectsNullJSON() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(null, ApplicationInput.class)
        );
    }

    @Test
    void readsTheRequestedOperation() {
        String json = INPUT_JSON.formatted(VALID_OPERATION);
        ApplicationInput input = JsonSanitizer.parseAndValidate(json, ApplicationInput.class);

        assertEquals("materialize-new-pushes", input.operation());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{",
        "[]",
        "{\"operation\":\"materialize-new-pushes\",\"operation\":\"other\"}"
    })
    void rejectsMalformedAndDuplicateJSON(String json) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, ApplicationInput.class)
        );

        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @Test
    void rejectsMissingOperationThroughSharedValidation() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate("{}", ApplicationInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @Test
    void rejectsTrailingJSON() {
        String json = INPUT_JSON.formatted(VALID_OPERATION) + " {}";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, ApplicationInput.class)
        );

        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @Test
    void rejectsNullInputThroughSharedValidation() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate("null", ApplicationInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\" \""})
    void rejectsNullOrBlankOperation(String operation) {
        String json = INPUT_JSON.formatted(operation);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, ApplicationInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

}
