package io.materializer.shared.repositorypushhistory.models;

import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.JsonSanitizer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryPushIDValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "\"id\":null,", "\"id\":0,", "\"id\":-1,"})
    void rejectsMissingNullAndNonpositivePushIDs(String id) {
        String json = """
                {%s"before":"%s","after":"%s","timestamp":"2026-11-07T01:00:00Z"}
                """.strip().formatted(id, "a".repeat(40), "b".repeat(40));

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RepositoryPush.class)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"pushID\":null,", "\"pushID\":0,", "\"pushID\":-1,"})
    void rejectsMissingNullAndNonpositiveSnapshotPushIDs(String id) {
        String json = """
                {%s"repositoryName":"owner/repo","branch":"main","commitSHA":"%s"}
                """.strip().formatted(id, "a".repeat(40));

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RepositorySnapshot.class)
        );
    }
}
