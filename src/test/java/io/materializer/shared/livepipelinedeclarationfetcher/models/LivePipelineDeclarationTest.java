package io.materializer.shared.livepipelinedeclarationfetcher.models;

import io.materializer.shared.utilities.JsonSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LivePipelineDeclarationTest {

    private static final String PIPELINE_JSON = """
            {"steps":[{"name":"build","operation":"build"}],
             "repositories":[{"repositoryName":"owner/pure-java","branch":"main",
              "build_system":"universal","platforms":["aarch64-linux","x86_64-linux"]}]}
            """;

    @Test
    void readsConfiguredRepositoriesAndSteps() {
        LivePipelineDeclaration livePipelineDeclaration = JsonSanitizer.parseAndValidate(
                PIPELINE_JSON,
                LivePipelineDeclaration.class
        );
        assertEquals(new DeclaredPipelineStep("build", "build"), livePipelineDeclaration.steps().getFirst());
        DeclaredPipelineInput repository = livePipelineDeclaration.repositories().getFirst();
        assertEquals("owner/pure-java", repository.repositoryName());
        assertEquals("universal", repository.buildSystem());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "{}",
        "[]",
        "{\"repositories\":[],\"steps\":[{\"name\":\"build\",\"operation\":\"build\"}]}",
        "{\"repositories\":[],\"steps\":[null]}",
        "{\"repositories\":[],\"steps\":[{}]}",
        "{\"steps\":[],\"repositories\":null}",
        "{\"steps\":[],\"repositories\":[null]}",
        "{\"steps\":[],\"repositories\":[{}]}"
    })
    void rejectsInvalidConfiguration(String json) {
        assertInvalid(json);
    }

    @Test
    void requiresExactlyOneBuildStepAtTheStart() {
        String build = """
                {"name":"Compile Libraries","operation":"build"}
                """.strip();
        String deploy = build.replace("build", "deploy").replace("Compile Libraries", "build");
        String declaration = PIPELINE_JSON.replace("{\"name\":\"build\",\"operation\":\"build\"}", "%s");

        assertInvalid(declaration.formatted(""));
        assertInvalid(declaration.formatted(deploy));
        assertInvalid(declaration.formatted(deploy + "," + build));
        assertInvalid(declaration.formatted(build + "," + build));
        JsonSanitizer.parseAndValidate(declaration.formatted(build), LivePipelineDeclaration.class);
    }

    @Test
    void rejectsUnsupportedOperationsBuildSystemsAndPlatforms() {
        assertInvalid(PIPELINE_JSON.replace("universal", "unsupported"));
        assertInvalid(PIPELINE_JSON.replace("aarch64-linux", "unsupported"));
        String extraStep = "{\"name\":\"Deploy\",\"operation\":\"deploy\"}";
        assertInvalid(PIPELINE_JSON.replace("\"operation\":\"build\"}", "\"operation\":\"build\"}," + extraStep));
    }

    @Test
    void validatesRepositoryAndPlatformValues() {
        assertInvalid(PIPELINE_JSON.replace("owner/pure-java", "invalid"));
        assertInvalid(PIPELINE_JSON.replace("aarch64-linux", ""));
    }

    @Test
    void requiresDistinctBranchesForRepeatedRepositories() {
        String entries = """
                {"steps":[{"name":"build","operation":"build"}],
                 "repositories":[
                  {"repositoryName":"owner/repo","branch":"main",
                   "build_system":"universal","platforms":["x86_64-linux"]},
                  {"repositoryName":"OWNER/REPO","branch":"%s",
                   "build_system":"universal","platforms":["x86_64-linux"]}
                ]}
                """.strip();

        assertInvalid(entries.formatted("main"));
        LivePipelineDeclaration livePipelineDeclaration = JsonSanitizer.parseAndValidate(
                entries.formatted("Main"),
                LivePipelineDeclaration.class
        );
        assertEquals(2, livePipelineDeclaration.repositories().size());
    }

    private void assertInvalid(String json) {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, LivePipelineDeclaration.class)
        );
    }
}
