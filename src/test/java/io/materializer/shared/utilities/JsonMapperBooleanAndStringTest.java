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

class JsonMapperBooleanAndStringTest {

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void acceptsJsonBooleans(String json) throws JsonProcessingException {
        Boolean expected = Boolean.valueOf(json);

        assertEquals(expected, JsonMapperUtility.MAPPER.readValue(json, Boolean.class));
        assertEquals(expected, JsonMapperUtility.MAPPER.readValue(json, boolean.class));
        assertEquals(
                new BooleanInput(expected),
                JsonSanitizer.parseAndValidate("{\"value\":" + json + "}", BooleanInput.class)
        );
        assertEquals(
                List.of(expected),
                JsonMapperUtility.MAPPER.readValue("[" + json + "]", new TypeReference<List<Boolean>>() { })
        );
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\"true\"",
                "\"false\"",
                "\"TRUE\"",
                "\"False\"",
                "\"tRuE\"",
                "\"1\"",
                "\"0\"",
                "\"null\"",
                "\"\"",
                "\"  \"",
                "\" true \"",
                "0",
                "1",
                "2",
                "-1",
                "0.0",
                "1.0",
                "1e0",
                "[]",
                "[true]",
                "[false]",
                "[null]",
                "{}",
                "{\"value\":true}"
            }
    )
    void rejectsNonBooleanValues(String json) {
        assertThrows(JsonProcessingException.class, () -> JsonMapperUtility.MAPPER.readValue(json, Boolean.class));
        assertThrows(JsonProcessingException.class, () -> JsonMapperUtility.MAPPER.readValue(json, boolean.class));
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("{\"value\":" + json + "}", BooleanInput.class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[" + json + "]", Boolean[].class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[" + json + "]", boolean[].class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[" + json + "]", new TypeReference<List<Boolean>>() { })
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue(
                        "{\"x\":" + json + "}",
                        new TypeReference<Map<String, Boolean>>() { }
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":null}"})
    void allowsNullAndMissingBooleanFields(String json) {
        assertEquals(new BooleanInput(null), JsonSanitizer.parseAndValidate(json, BooleanInput.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":null}"})
    void leavesRequiredBooleanFieldsForHibernate(String json) throws JsonProcessingException {
        assertNull(JsonMapperUtility.MAPPER.readValue(json, RequiredBooleanInput.class).value());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RequiredBooleanInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @Test
    void preservesBooleanNullRules() throws JsonProcessingException {
        assertNull(JsonMapperUtility.MAPPER.readValue("null", Boolean.class));
        assertThrows(JsonProcessingException.class, () -> JsonMapperUtility.MAPPER.readValue("null", boolean.class));
        assertArrayEquals(
                new Boolean[]{true, null, false},
                JsonMapperUtility.MAPPER.readValue("[true,null,false]", Boolean[].class)
        );
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\"hello\"",
                "\"1\"",
                "\"1.0\"",
                "\"true\"",
                "\"false\"",
                "\"\"",
                "\"  \"",
                "\"null\"",
                "\"line\\nbreak\"",
                "\"quote\\\"inside\"",
                "\"雪\""
            }
    )
    void acceptsQuotedJsonStrings(String json) throws JsonProcessingException {
        String expected = JsonMapperUtility.MAPPER.readTree(json).textValue();

        assertEquals(expected, JsonMapperUtility.MAPPER.readValue(json, String.class));
        assertEquals(
                new StringInput(expected),
                JsonSanitizer.parseAndValidate("{\"value\":" + json + "}", StringInput.class)
        );
        assertEquals(
                List.of(expected),
                JsonMapperUtility.MAPPER.readValue("[" + json + "]", new TypeReference<List<String>>() { })
        );
        assertArrayEquals(
                new String[]{expected},
                JsonMapperUtility.MAPPER.readValue("[" + json + "]", String[].class)
        );
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1",
                "-1",
                "0",
                "1.0",
                "1e2",
                "1e400",
                "true",
                "false",
                "[]",
                "[\"x\"]",
                "{}",
                "{\"x\":\"y\"}"
            }
    )
    void rejectsNonStringValues(String json) {
        assertThrows(JsonProcessingException.class, () -> JsonMapperUtility.MAPPER.readValue(json, String.class));
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("{\"value\":" + json + "}", StringInput.class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[" + json + "]", new TypeReference<List<String>>() { })
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[" + json + "]", String[].class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue(
                        "{\"x\":" + json + "}",
                        new TypeReference<Map<String, String>>() { }
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":null}"})
    void allowsNullAndMissingStringFields(String json) {
        assertEquals(new StringInput(null), JsonSanitizer.parseAndValidate(json, StringInput.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value\":null}"})
    void leavesRequiredStringFieldsForHibernate(String json) throws JsonProcessingException {
        assertNull(JsonMapperUtility.MAPPER.readValue(json, RequiredStringInput.class).value());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RequiredStringInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @Test
    void preservesStringNullValues() throws JsonProcessingException {
        assertNull(JsonMapperUtility.MAPPER.readValue("null", String.class));
        assertArrayEquals(
                new String[]{"x", null},
                JsonMapperUtility.MAPPER.readValue("[\"x\",null]", String[].class)
        );
    }

    @Test
    void leavesMapKeysAndUntypedValuesAlone() throws JsonProcessingException {
        assertEquals(
                Map.of(true, "yes", false, "no"),
                JsonMapperUtility.MAPPER.readValue(
                        "{\"true\":\"yes\",\"false\":\"no\"}",
                        new TypeReference<Map<Boolean, String>>() { }
                )
        );
        assertEquals(
                Map.of("1", 1, "true", true),
                JsonMapperUtility.MAPPER.readValue(
                        "{\"1\":1,\"true\":true}",
                        new TypeReference<Map<String, Object>>() { }
                )
        );
    }

    record BooleanInput(Boolean value) { }

    record RequiredBooleanInput(@NotNull Boolean value) { }

    record StringInput(String value) { }

    record RequiredStringInput(@NotNull String value) { }
}
