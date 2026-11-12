package io.materializer.shared.utilities;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonMapperEnumTest {

    @ParameterizedTest
    @ValueSource(strings = {"\"FIRST\"", "\"SECOND\""})
    void acceptsEnumNames(String json) throws JsonProcessingException {
        Choice expected = Choice.valueOf(JsonMapperUtility.MAPPER.readTree(json).textValue());

        assertEquals(expected, JsonMapperUtility.MAPPER.readValue(json, Choice.class));
        assertEquals(
                new EnumInput(expected),
                JsonSanitizer.parseAndValidate("{\"value\":" + json + "}", EnumInput.class)
        );
        assertEquals(
                List.of(expected),
                JsonMapperUtility.MAPPER.readValue("[" + json + "]", new TypeReference<List<Choice>>() { })
        );
        assertArrayEquals(
                new Choice[]{expected},
                JsonMapperUtility.MAPPER.readValue("[" + json + "]", Choice[].class)
        );
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0",
                "1",
                "-1",
                "2",
                "1.0",
                "1e0",
                "\"0\"",
                "\"1\"",
                "\"-1\"",
                "\"2\"",
                "\" 0 \"",
                "\"1.0\"",
                "\"1e0\""
            }
    )
    void rejectsNumericOrdinals(String json) {
        assertThrows(JsonProcessingException.class, () -> JsonMapperUtility.MAPPER.readValue(json, Choice.class));
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("{\"value\":" + json + "}", EnumInput.class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[" + json + "]", new TypeReference<List<Choice>>() { })
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[" + json + "]", Choice[].class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue(
                        "{\"x\":" + json + "}",
                        new TypeReference<Map<String, Choice>>() { }
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":null}"})
    void allowsNullAndMissingEnumFields(String json) {
        assertEquals(new EnumInput(null), JsonSanitizer.parseAndValidate(json, EnumInput.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":null}"})
    void leavesRequiredEnumFieldsForHibernate(String json) throws JsonProcessingException {
        assertNull(JsonMapperUtility.MAPPER.readValue(json, RequiredEnumInput.class).value());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RequiredEnumInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @Test
    void preservesNullEnumValues() throws JsonProcessingException {
        assertNull(JsonMapperUtility.MAPPER.readValue("null", Choice.class));
        assertArrayEquals(
                new Choice[]{null, Choice.FIRST},
                JsonMapperUtility.MAPPER.readValue("[null,\"FIRST\"]", Choice[].class)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"first\"", "\"UNKNOWN\"", "true", "false", "[]", "{}"})
    void keepsOtherEnumChecks(String json) {
        assertThrows(JsonProcessingException.class, () -> JsonMapperUtility.MAPPER.readValue(json, Choice.class));
    }

    @Test
    void keepsEnumMapKeysAsNames() throws JsonProcessingException {
        assertEquals(
                Map.of(Choice.FIRST, "x"),
                JsonMapperUtility.MAPPER.readValue(
                        "{\"FIRST\":\"x\"}",
                        new TypeReference<Map<Choice, String>>() { }
                )
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue(
                        "{\"0\":\"x\"}",
                        new TypeReference<Map<Choice, String>>() { }
                )
        );
    }

    record EnumInput(Choice value) { }

    record RequiredEnumInput(@NotNull Choice value) { }

    enum Choice { FIRST, SECOND }
}
