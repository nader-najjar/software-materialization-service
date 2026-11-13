package io.materializer.workflowgeneration;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.ResourceUtility;
import io.materializer.shared.utilities.YamlMapperUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowGeneratorTest {

    @Test
    void generatesTheExpectedWorkflowYaml() throws JsonProcessingException {
        LivePipelineDeclarationFetcher fetcher = mock(LivePipelineDeclarationFetcher.class);
        WorkflowGenerator generator = new WorkflowGenerator(fetcher, new BuildJobGenerator());
        RepositorySnapshot materializer = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);
        List<String> platforms = List.of("aarch64-linux", "x86_64-linux");
        List<DeclaredPipelineInput> repositories = List.of(
                new DeclaredPipelineInput("nader-najjar/pure-java", "main", "universal", platforms),
                new DeclaredPipelineInput("nader-najjar/pure-javascript", "main", "universal", platforms)
        );
        String javaSHA = "28e79fd674af4ba6387765247272fed25578430d";
        String javascriptSHA = "e90d63d9b81312d83738bb1654442d54ad40e36e";
        List<RepositorySnapshot> sources = List.of(
                new RepositorySnapshot("nader-najjar/pure-java", "main", javaSHA, 1L),
                new RepositorySnapshot("nader-najjar/pure-javascript", "main", javascriptSHA, 2L)
        );
        DeclaredPipelineStep step = new DeclaredPipelineStep("Build", "build");
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(repositories, List.of(step));
        when(fetcher.fetchAndValidate(materializer)).thenReturn(declaration);

        String yaml = generator.generate(new LivePipelineInputs(materializer, sources));
        String expected = ResourceUtility.readString("/workflowgeneration/materialize.yml");
        assertEquals(YamlMapperUtility.MAPPER.readTree(expected), YamlMapperUtility.MAPPER.readTree(yaml));
    }

    @Test
    void rejectsUnsupportedOperations() {
        LivePipelineDeclarationFetcher fetcher = mock(LivePipelineDeclarationFetcher.class);
        WorkflowGenerator generator = new WorkflowGenerator(fetcher, new BuildJobGenerator());
        RepositorySnapshot materializer = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);
        LivePipelineInputs inputs = new LivePipelineInputs(materializer, List.of());
        DeclaredPipelineStep build = new DeclaredPipelineStep("Compile Libraries", "build");
        DeclaredPipelineStep unknown = new DeclaredPipelineStep("build", "unknown");
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(List.of(), List.of(build, unknown));
        when(fetcher.fetchAndValidate(materializer)).thenReturn(declaration);

        assertEquals("Unsupported pipeline operation: unknown",
                assertThrows(IllegalArgumentException.class, () -> generator.generate(inputs)).getMessage());
    }

}
