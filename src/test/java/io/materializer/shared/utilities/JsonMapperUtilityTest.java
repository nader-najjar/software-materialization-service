package io.materializer.shared.utilities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMapperUtilityTest {

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"number\":null,\"enabled\":null,\"text\":null}"})
    void allowsMissingAndNullRecordFieldsWithoutNotNull(String json) throws JsonProcessingException {
        NullableInput expected = new NullableInput(null, null, null);

        assertEquals(expected, JsonMapperUtility.MAPPER.readValue(json, NullableInput.class));
        assertEquals(expected, JsonSanitizer.parseAndValidate(json, NullableInput.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"number\":null,\"enabled\":null,\"text\":null}"})
    void leavesNotNullChecksForHibernate(String json) throws JsonProcessingException {
        assertEquals(
                new RequiredInput(null, null, null),
                JsonMapperUtility.MAPPER.readValue(json, RequiredInput.class)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RequiredInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("number:"));
        assertTrue(exception.getCause().getMessage().contains("enabled:"));
        assertTrue(exception.getCause().getMessage().contains("text:"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":null}"})
    void allowsMissingAndNullAnnotatedConstructorFields(String json) throws JsonProcessingException {
        assertNull(JsonMapperUtility.MAPPER.readValue(json, ConstructorInput.class).value);
        assertNull(JsonSanitizer.parseAndValidate(json, ConstructorInput.class).value);
    }

    @Test
    void keepsDefaultsForMissingMutableFields() {
        assertEquals(7, JsonSanitizer.parseAndValidate("{}", MutableInput.class).value);
    }

    @Test
    void validatesExplicitNullInMutableFields() throws JsonProcessingException {
        String json = "{\"value\":null}";
        assertNull(JsonMapperUtility.MAPPER.readValue(json, MutableInput.class).value);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, MutableInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @Test
    void validatesMissingNestedFieldsWithValid() throws JsonProcessingException {
        String json = "{\"child\":{}}";
        assertEquals(
                new NestedInput(new RequiredInput(null, null, null)),
                JsonMapperUtility.MAPPER.readValue(json, NestedInput.class)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, NestedInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("child.number:"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":null}"})
    void stillRejectsMissingAndNullPrimitiveRecordFields(String json) {
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue(json, PrimitiveInput.class)
        );
    }

    @Test
    void stillHonorsJacksonRequiredFields() {
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("{}", JacksonRequiredInput.class)
        );
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"number\":1.0}",
                "{\"number\":1e0}",
                "{\"number\":true}",
                "{\"number\":2147483648}",
                "{\"number\":[1]}",
                "{\"number\":1,\"number\":2}",
                "{} {}",
                "{"
            }
    )
    void keepsExistingTypeAndJsonChecks(String json) {
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue(json, NullableInput.class)
        );
    }

    record NullableInput(Integer number, Boolean enabled, String text) { }

    record RequiredInput(@NotNull Integer number, @NotNull Boolean enabled, @NotNull String text) { }

    record NestedInput(@Valid RequiredInput child) { }

    record PrimitiveInput(int value) { }

    record JacksonRequiredInput(@JsonProperty(value = "value", required = true) Integer value) { }

    static final class ConstructorInput {

        private final Integer value;

        @JsonCreator
        ConstructorInput(@JsonProperty("value") Integer value) {
            this.value = value;
        }
    }

    static final class MutableInput {

        @JsonProperty("value")
        @NotNull
        private Integer value = 7;
    }
}
