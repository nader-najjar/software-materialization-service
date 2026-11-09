package io.materializer.shared.utilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.ValidationException;

public final class JsonSanitizer {

    private JsonSanitizer() { }

    public static <T> T parseAndValidate(String json, Class<T> type) {
        if (json == null) {
            throw new IllegalArgumentException("JSON must not be null");
        }

        try {
            T value = JsonMapperUtility.MAPPER.readValue(json, type);
            HibernateValidatorUtility.validate(value);
            return value;

        } catch (JsonProcessingException | ValidationException exception) {
            throw new IllegalArgumentException(
                    "Invalid " + type.getSimpleName() + ": " + exception.getMessage(),
                    exception
            );
        }
    }
}
