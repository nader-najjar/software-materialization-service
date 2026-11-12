package io.materializer.shared.utilities;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMapperNumberTest {

    private static final List<String> EMPTY_NUMERIC_STRINGS = List.of(
            "\"\"",
            "\" \"",
            "\"   \"",
            "\"\\t\"",
            "\"\\n\"",
            "\"\\r\\n\"",
            "\" \\t\\r\\n \""
    );

    @ParameterizedTest
    @MethodSource("numericTypes")
    void rejectsQuotedNumbers(Class<?> type) {
        for (String json : List.of("\"1\"", "\"-1\"", "\"1.0\"", "\"1e0\"", "\" 1 \"")) {
            assertThrows(
                    JsonProcessingException.class,
                    () -> JsonMapperUtility.MAPPER.readValue(json, type),
                    () -> type.getSimpleName() + " must reject " + json
            );
        }
    }

    @ParameterizedTest
    @MethodSource("numericTypes")
    void rejectsEmptyAndBlankNumericStrings(Class<?> type) {
        for (String json : EMPTY_NUMERIC_STRINGS) {
            assertThrows(
                    JsonProcessingException.class,
                    () -> JsonMapperUtility.MAPPER.readValue(json, type),
                    () -> type.getSimpleName() + " must reject " + json
            );
        }
    }

    @ParameterizedTest
    @MethodSource("boxedNumericTypes")
    void rejectsEmptyAndBlankNestedNumericValues(Class<?> type) {
        JavaType recordType = JsonMapperUtility.MAPPER.getTypeFactory().constructParametricType(
                NumericValue.class,
                type
        );
        JavaType listType = JsonMapperUtility.MAPPER.getTypeFactory().constructCollectionType(List.class, type);
        JavaType mapType = JsonMapperUtility.MAPPER.getTypeFactory().constructMapType(Map.class, String.class, type);
        JavaType nestedType = JsonMapperUtility.MAPPER.getTypeFactory().constructCollectionType(List.class, mapType);

        for (String json : EMPTY_NUMERIC_STRINGS) {
            assertThrows(
                    JsonProcessingException.class,
                    () -> JsonMapperUtility.MAPPER.readValue("{\"value\":" + json + "}", recordType)
            );
            assertThrows(
                    JsonProcessingException.class,
                    () -> JsonMapperUtility.MAPPER.readValue("[" + json + "]", listType)
            );
            assertThrows(
                    JsonProcessingException.class,
                    () -> JsonMapperUtility.MAPPER.readValue("{\"value\":" + json + "}", mapType)
            );
            assertThrows(
                    JsonProcessingException.class,
                    () -> JsonMapperUtility.MAPPER.readValue("[{\"value\":" + json + "}]", nestedType)
            );
        }
    }

    @ParameterizedTest
    @MethodSource("boxedNumericTypes")
    void preservesMissingAndNullNumericValues(Class<?> type) throws JsonProcessingException {
        JavaType recordType = JsonMapperUtility.MAPPER.getTypeFactory().constructParametricType(
                NumericValue.class,
                type
        );
        NumericValue<?> missing = JsonMapperUtility.MAPPER.readValue("{}", recordType);
        NumericValue<?> explicitNull = JsonMapperUtility.MAPPER.readValue("{\"value\":null}", recordType);

        assertNull(missing.value());
        assertNull(explicitNull.value());

        JavaType listType = JsonMapperUtility.MAPPER.getTypeFactory().constructCollectionType(List.class, type);
        List<?> list = JsonMapperUtility.MAPPER.readValue("[null]", listType);
        assertEquals(1, list.size());
        assertNull(list.getFirst());

        JavaType mapType = JsonMapperUtility.MAPPER.getTypeFactory().constructMapType(Map.class, String.class, type);
        Map<?, ?> map = JsonMapperUtility.MAPPER.readValue("{\"value\":null}", mapType);
        assertEquals(1, map.size());
        assertNull(map.get("value"));
    }

    @ParameterizedTest
    @MethodSource("numericTypes")
    void stillAcceptsJsonNumbers(Class<?> type) throws JsonProcessingException {
        Number value = (Number) JsonMapperUtility.MAPPER.readValue("1", type);

        assertEquals(1.0, value.doubleValue());
    }

    @ParameterizedTest
    @MethodSource("numericTypes")
    void keepsExistingNullBehavior(Class<?> type) throws JsonProcessingException {
        if (type.isPrimitive()) {
            assertThrows(JsonProcessingException.class, () -> JsonMapperUtility.MAPPER.readValue("null", type));

        } else {
            assertNull(JsonMapperUtility.MAPPER.readValue("null", type));
        }
    }

    @Test
    void stillAcceptsJsonDecimalsAndExponentsForFloatingPoint() throws JsonProcessingException {
        assertEquals(1.5f, JsonMapperUtility.MAPPER.readValue("1.5", Float.class));
        assertEquals(100.0, JsonMapperUtility.MAPPER.readValue("1e2", Double.class));
        assertEquals(new BigDecimal("1.5"), JsonMapperUtility.MAPPER.readValue("1.5", BigDecimal.class));
    }

    @ParameterizedTest
    @MethodSource("floatingPointTypes")
    void preservesSpecialFloatingPointStrings(Class<?> type) throws JsonProcessingException {
        Number notANumber = (Number) JsonMapperUtility.MAPPER.readValue("\"NaN\"", type);
        assertTrue(Double.isNaN(notANumber.doubleValue()));

        for (String json : List.of("\"Infinity\"", "\"INF\"")) {
            Number positiveInfinity = (Number) JsonMapperUtility.MAPPER.readValue(json, type);
            assertEquals(Double.POSITIVE_INFINITY, positiveInfinity.doubleValue());
        }
        for (String json : List.of("\"-Infinity\"", "\"-INF\"")) {
            Number negativeInfinity = (Number) JsonMapperUtility.MAPPER.readValue(json, type);
            assertEquals(Double.NEGATIVE_INFINITY, negativeInfinity.doubleValue());
        }
    }

    @ParameterizedTest
    @MethodSource("floatingPointTypes")
    void stillRejectsUnsupportedSpecialFloatingPointStrings(Class<?> type) {
        List<String> rejected = List.of(
                "\"+Infinity\"",
                "\"+INF\"",
                "\"+NaN\"",
                "\"-NaN\"",
                "\"NAN\"",
                "\"nan\"",
                "\"infinity\"",
                "\"Inf\"",
                "\"inf\"",
                "\" NaN \"",
                "\" Infinity \"",
                "\" -Infinity \"",
                "\" INF \"",
                "\" -INF \""
        );
        for (String json : rejected) {
            assertThrows(
                    JsonProcessingException.class,
                    () -> JsonMapperUtility.MAPPER.readValue(json, type)
            );
        }
    }

    @Test
    void rejectsQuotedNumbersInRecordsAndContainers() {
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("{\"value\":\"1\"}", NumericInput.class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[\"1\"]", Integer[].class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[\"1\"]", int[].class)
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("[\"1\"]", new TypeReference<List<Double>>() { })
        );
        assertThrows(
                JsonProcessingException.class,
                () -> JsonMapperUtility.MAPPER.readValue("{\"x\":\"1\"}", new TypeReference<Map<String, Long>>() { })
        );
    }

    @Test
    void leavesNonNumericTargetsAndMapKeysAlone() throws JsonProcessingException {
        assertEquals("1", JsonMapperUtility.MAPPER.readValue("\"1\"", String.class));
        assertEquals(Instant.ofEpochSecond(1), JsonMapperUtility.MAPPER.readValue("\"1\"", Instant.class));
        assertEquals(ExampleChoice.FIRST, JsonMapperUtility.MAPPER.readValue("\"FIRST\"", ExampleChoice.class));
        assertEquals(
                Map.of(1, "x"),
                JsonMapperUtility.MAPPER.readValue("{\"1\":\"x\"}", new TypeReference<Map<Integer, String>>() { })
        );
    }

    static Stream<Class<?>> numericTypes() {
        return Stream.of(
                byte.class,
                Byte.class,
                short.class,
                Short.class,
                int.class,
                Integer.class,
                long.class,
                Long.class,
                float.class,
                Float.class,
                double.class,
                Double.class,
                BigInteger.class,
                BigDecimal.class,
                Number.class
        );
    }

    static Stream<Class<?>> boxedNumericTypes() {
        return Stream.of(
                Short.class,
                Integer.class,
                Long.class,
                Float.class,
                Double.class
        );
    }

    static Stream<Class<?>> floatingPointTypes() {
        return Stream.of(
                float.class,
                Float.class,
                double.class,
                Double.class
        );
    }

    record NumericInput(Integer value) { }

    record NumericValue<T>(T value) { }

    enum ExampleChoice { FIRST }
}
