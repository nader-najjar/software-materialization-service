package io.materializer.shared.utilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class ResourceUtility {

    private ResourceUtility() { }

    public static String readString(String resourcePath) {
        try (InputStream content = ResourceUtility.class.getResourceAsStream(resourcePath)) {
            if (content == null) {
                throw new IllegalStateException("Missing resource: " + resourcePath);
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException exception) {
            String message = "Could not read resource: " + resourcePath;
            throw new UncheckedIOException(message, exception);
        }
    }
}
