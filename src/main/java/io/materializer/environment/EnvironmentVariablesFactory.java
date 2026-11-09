package io.materializer.environment;

import java.util.Arrays;
import java.util.Map;

import io.materializer.environment.exceptions.EnvironmentVariableException;
import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import jakarta.validation.ValidationException;

public final class EnvironmentVariablesFactory {

    private EnvironmentVariablesFactory() { }

    public static EnvironmentVariables from(Map<String, String> environment) {
        EnvironmentVariables environmentVariables = new EnvironmentVariables(
                extractString(environment, "GITHUB_TOKEN"),
                extractString(environment, "MATERIALIZER_REGISTRY_REPOSITORY"),
                extractString(environment, "PIPELINE_STRUCTURE_STORE_REPOSITORY"),
                extractString(environment, "MATERIALIZATION_SERVICE_REPOSITORY")
        );

        try {
            HibernateValidatorUtility.validate(environmentVariables);

        } catch (ValidationException exception) {
            throw new EnvironmentVariableException("Environment validation failed", exception);
        }

        return environmentVariables;
    }

    static String extractString(Map<String, String> environment, String key) {
        String value = environment.get(key);
        ensureVariableExists(key, value);
        return value;
    }

    static int extractInt(Map<String, String> environment, String key) {
        String value = environment.get(key);
        ensureVariableExists(key, value);

        try {
            return Integer.parseInt(value);

        } catch (NumberFormatException e) {
            String message = "Environment variable '" + key + "' must be a valid integer, got: " + value;
            throw new EnvironmentVariableException(message, e);
        }
    }

    static boolean extractBoolean(Map<String, String> environment, String key) {
        String value = environment.get(key);
        ensureVariableExists(key, value);

        return customParseBoolean(key, value);
    }

    static <E extends Enum<E>> E extractEnum(Map<String, String> environment, String key, Class<E> type) {
        String value = environment.get(key);
        ensureVariableExists(key, value);

        try {
            return Enum.valueOf(type, value);

        } catch (IllegalArgumentException e) {
            String allowedValues = Arrays.toString(type.getEnumConstants());
            String message = "Environment variable '" + key + "' must be one of " + allowedValues + ", got: " + value;
            throw new EnvironmentVariableException(message, e);
        }
    }

    static void ensureVariableExists(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new EnvironmentVariableException("Required environment variable '" + key + "' is not set");
        }
    }

    static boolean customParseBoolean(String key, String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }

        String message = "Environment variable '" + key + "' must be 'true' or 'false', got: " + value;
        throw new EnvironmentVariableException(message);
    }
}
