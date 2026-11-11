package io.materializer.workflowgeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import io.materializer.shared.models.DeclaredPipelineInput;
import io.materializer.shared.models.DeclaredPipelineStep;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.ResourceUtility;
import io.materializer.workflowgeneration.models.BuildJob;
import io.materializer.workflowgeneration.models.BuildMatrixRow;
import io.materializer.workflowgeneration.models.JobStep;

public class BuildJobGenerator {

    @Inject
    public BuildJobGenerator() { }

    public BuildJob generate(
            DeclaredPipelineStep step,
            List<DeclaredPipelineInput> repositories,
            List<RepositorySnapshot> sourceSnapshots
    ) {
        List<BuildMatrixRow> matrixRows = generateBuildMatrixRows(repositories, sourceSnapshots);
        BuildJob.Matrix matrix = new BuildJob.Matrix(matrixRows);
        BuildJob.Strategy strategy = new BuildJob.Strategy(false, matrix);

        String name = step.name() + " ${{ matrix.repositoryName }} (${{ matrix.platform }})";

        return new BuildJob(
                name,
                "${{ matrix.runner }}",
                strategy,
                List.of(
                    generateRetryGuard(),
                    generateUnsupportedBuildSystemGuard(),
                    generateCheckout(),
                    generateBuild(),
                    generateSetArtifactRepositoryName(),
                    generateUploadReleaseArtifact()
                )
        );
    }

    static List<BuildMatrixRow> generateBuildMatrixRows(
            List<DeclaredPipelineInput> repositories,
            List<RepositorySnapshot> sourceSnapshots
    ) {
        List<BuildMatrixRow> rows = new ArrayList<>();
        for (DeclaredPipelineInput repository : repositories) {
            rows.addAll(generateRepositoryMatrixRows(repository, sourceSnapshots));
        }
        return rows.stream().distinct().toList();
    }

    private static JobStep generateRetryGuard() {
        return new JobStep(
                "Reject Manual Retries",
                null,
                "$/.github/actions/reject-manual-retries",
                null,
                null,
                null
        );
    }

    private static JobStep generateUnsupportedBuildSystemGuard() {
        return new JobStep(
                "Reject Unsupported Build Systems",
                "matrix.buildSystem != 'universal'",
                null,
                ResourceUtility.readString("/workflowgeneration/reject-unsupported-build-system.sh"),
                null,
                Map.of(
                    "BUILD_SYSTEM", "${{ matrix.buildSystem }}"
                )
        );
    }

    private static JobStep generateCheckout() {
        return new JobStep(
                "Check Out ${{ matrix.repositoryName }}",
                null,
                "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
                null,
                Map.of(
                        "repository", "${{ matrix.repositoryName }}",
                        "ref", "${{ matrix.commitSHA }}",
                        "persist-credentials", "false"
                ),
                null
        );
    }

    private static JobStep generateBuild() {
        return new JobStep(
                "Build ${{ matrix.repositoryName }}",
                "matrix.buildSystem == 'universal'",
                "$/.github/actions/universal-build",
                null,
                null,
                null
        );
    }

    private static JobStep generateSetArtifactRepositoryName() {
        return new JobStep(
                "Set Artifact Repository Name",
                null,
                null,
                "echo \"ARTIFACT_REPOSITORY=${REPOSITORY_NAME/\\//@}\" >> \"$GITHUB_ENV\"",
                null,
                Map.of("REPOSITORY_NAME", "${{ matrix.repositoryName }}")
        );
    }

    private static JobStep generateUploadReleaseArtifact() {
        return new JobStep(
                "Upload Release Artifact",
                null,
                "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
                null,
                Map.of(
                        "name", "${{ env.ARTIFACT_REPOSITORY }}-${{ matrix.platform }}-${{ matrix.commitSHA }}",
                        "path", "build/release-artifact.tar.gz",
                        "if-no-files-found", "error"
                ),
                null
        );
    }

    private static List<BuildMatrixRow> generateRepositoryMatrixRows(
            DeclaredPipelineInput repository,
            List<RepositorySnapshot> sourceSnapshots
    ) {
        RepositorySnapshot snapshot = sourceSnapshots.stream()
                .filter(pin -> pin.repositoryName().equalsIgnoreCase(repository.repositoryName()))
                .filter(pin -> pin.branch().equals(repository.branch()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing source pin for %s@%s".formatted(repository.repositoryName(), repository.branch())
                ));
        List<BuildMatrixRow> rows = new ArrayList<>();
        for (String platform : repository.platforms()) {
            rows.add(new BuildMatrixRow(
                    repository.repositoryName(),
                    snapshot.commitSHA(),
                    repository.buildSystem(),
                    platform,
                    runnerForPlatform(platform)
            ));
        }
        return rows;
    }

    private static String runnerForPlatform(String platform) {
        return switch (platform) {
            case "aarch64-linux" -> "ubuntu-24.04-arm";
            case "x86_64-linux" -> "ubuntu-24.04";
            default -> throw new IllegalArgumentException("Unsupported build platform: " + platform);
        };
    }
}
