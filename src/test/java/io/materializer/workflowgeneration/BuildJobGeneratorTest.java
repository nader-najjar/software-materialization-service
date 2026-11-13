package io.materializer.workflowgeneration;

import java.util.List;

import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.workflowgeneration.models.BuildMatrixRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildJobGeneratorTest {

    @Test
    void includesTheDeclaredNameRepositoryAndPlatformInTheBuildJobName() {
        DeclaredPipelineStep step = new DeclaredPipelineStep("Compile Libraries", "build");

        assertEquals(
                "Compile Libraries ${{ matrix.repositoryName }} (${{ matrix.platform }})",
                new BuildJobGenerator().generate(step, List.of(), List.of()).name()
        );
    }

    @Test
    void buildsEachPlatformWithThePinForTheDeclaredBranch() {
        DeclaredPipelineInput repository = new DeclaredPipelineInput(
                "owner/source",
                "release",
                "universal",
                List.of("aarch64-linux", "x86_64-linux")
        );
        RepositorySnapshot main = new RepositorySnapshot("owner/source", "main", "a".repeat(40), 1L);
        RepositorySnapshot release = new RepositorySnapshot("OWNER/source", "release", "b".repeat(40), 2L);

        List<BuildMatrixRow> rows = BuildJobGenerator.generateBuildMatrixRows(
                List.of(repository),
                List.of(main, release)
        );

        String releaseSHA = release.commitSHA();
        assertEquals(List.of(
                new BuildMatrixRow("owner/source", releaseSHA, "universal", "aarch64-linux", "ubuntu-24.04-arm"),
                new BuildMatrixRow("owner/source", releaseSHA, "universal", "x86_64-linux", "ubuntu-24.04")
        ), rows);
    }

    @Test
    void buildsIdenticalPinsOnlyOnceAcrossBranchesAndDuplicatePlatforms() {
        List<String> platforms = List.of("x86_64-linux", "x86_64-linux", "aarch64-linux");
        List<DeclaredPipelineInput> inputs = List.of(
                new DeclaredPipelineInput("owner/source", "main", "universal", platforms),
                new DeclaredPipelineInput("owner/source", "release", "universal", platforms)
        );
        String commitSHA = "a".repeat(40);
        List<RepositorySnapshot> snapshots = List.of(
                new RepositorySnapshot("owner/source", "main", commitSHA, 1L),
                new RepositorySnapshot("owner/source", "release", commitSHA, 2L)
        );

        assertEquals(List.of(
                new BuildMatrixRow("owner/source", commitSHA, "universal", "x86_64-linux", "ubuntu-24.04"),
                new BuildMatrixRow("owner/source", commitSHA, "universal", "aarch64-linux", "ubuntu-24.04-arm")
        ), BuildJobGenerator.generateBuildMatrixRows(inputs, snapshots));
    }

    @Test
    void rejectsMissingPinsAndUnsupportedPlatforms() {
        DeclaredPipelineInput input = new DeclaredPipelineInput("owner/source", "main", "universal", List.of("other"));
        RepositorySnapshot snapshot = new RepositorySnapshot("owner/source", "main", "a".repeat(40), 1L);
        assertThrows(
                IllegalStateException.class,
                () -> BuildJobGenerator.generateBuildMatrixRows(List.of(input), List.of())
        );
        assertThrows(IllegalArgumentException.class,
                () -> BuildJobGenerator.generateBuildMatrixRows(List.of(input), List.of(snapshot)));
    }
}
