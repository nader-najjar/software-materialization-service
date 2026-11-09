package io.materializer.pipelinestructureplanner.models;

import io.materializer.shared.utilities.JsonSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeclaredPipelineInputsTest {

    private static final String PIPELINE_JSON = """
            {"steps":[{"name":"build","operation":"build","software":"all","platforms":"all"}],
             "repositories":[{"repositoryName":"owner/pure-java","branch":"main",
              "build_system":"universal","platforms":["aarch64-linux","x86_64-linux"]}]}
            """;

    @Test
    void readsConfiguredRepositories() {
        DeclaredPipelineInputs declaredInputs = JsonSanitizer.parseAndValidate(
                PIPELINE_JSON,
                DeclaredPipelineInputs.class
        );
        PipelineInput repository = declaredInputs.repositories().getFirst();
        assertEquals("owner/pure-java", repository.repositoryName());
        assertEquals("universal", repository.buildSystem());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "{}",
        "[]",
        "{\"steps\":[],\"repositories\":null}",
        "{\"steps\":[],\"repositories\":[null]}",
        "{\"steps\":[],\"repositories\":[{}]}"
    })
    void rejectsInvalidConfiguration(String json) {
        assertInvalid(json);
    }

    @Test
    void validatesRepositoryAndPlatformValues() {
        assertInvalid(PIPELINE_JSON.replace("owner/pure-java", "invalid"));
        assertInvalid(PIPELINE_JSON.replace("aarch64-linux", ""));
    }

    @Test
    void requiresDistinctBranchesForRepeatedRepositories() {
        String entries = """
                {"steps":[],"repositories":[
                  {"repositoryName":"owner/repo","branch":"main",
                   "build_system":"universal","platforms":["linux"]},
                  {"repositoryName":"OWNER/REPO","branch":"%s",
                   "build_system":"universal","platforms":["linux"]}
                ]}
                """.strip();

        assertInvalid(entries.formatted("main"));
        DeclaredPipelineInputs declaredInputs = JsonSanitizer.parseAndValidate(
                entries.formatted("Main"),
                DeclaredPipelineInputs.class
        );
        assertEquals(2, declaredInputs.repositories().size());
    }

    private void assertInvalid(String json) {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, DeclaredPipelineInputs.class)
        );
    }
}
