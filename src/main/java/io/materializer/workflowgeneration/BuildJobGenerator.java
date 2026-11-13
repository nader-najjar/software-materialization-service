package io.materializer.workflowgeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.workflowgeneration.models.BuildJob;
import io.materializer.workflowgeneration.models.BuildMatrixRow;
import io.materializer.workflowgeneration.models.JobStep;

public class BuildJobGenerator {

    @Inject
    public BuildJobGenerator() { }

    public BuildJob generate(
            DeclaredPipelineStep step,
            List<DeclaredPipelineInput> repositories,
            List<RepositorySnapshot> declaredRepositorySnapshots
    ) {
        List<BuildMatrixRow> matrixRows = generateBuildMatrixRows(repositories, declaredRepositorySnapshots);
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
                    generateSetReleaseName(),
                    generateSetRepositoryOwner(),
                    generateCreateRepositoryToken(),
                    generateFindReleaseArtifact(),
                    generateCheckout(),
                    generateBuild(),
                    generatePublishReleaseArtifact()
                )
        );
    }

    static List<BuildMatrixRow> generateBuildMatrixRows(
            List<DeclaredPipelineInput> repositories,
            List<RepositorySnapshot> declaredRepositorySnapshots
    ) {
        List<BuildMatrixRow> rows = new ArrayList<>();
        for (DeclaredPipelineInput repository : repositories) {
            rows.addAll(generateRepositoryMatrixRows(repository, declaredRepositorySnapshots));
        }
        return rows.stream().distinct().toList();
    }

    private static JobStep generateRetryGuard() {
        return new JobStep(
                "Reject Manual Retries",
                null,
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
                null,
                "matrix.buildSystem != 'universal'",
                null,
                "echo \"::error::Unsupported build system: $BUILD_SYSTEM\"; exit 1",
                null,
                Map.of(
                    "BUILD_SYSTEM", "${{ matrix.buildSystem }}"
                )
        );
    }

    private static JobStep generateSetReleaseName() {
        return new JobStep(
                "Set Release Name",
                null,
                null,
                null,
                "repo='${{ matrix.repositoryName }}'; "
                        + "echo \"RELEASE_NAME=${repo/\\//@}-${{ matrix.platform }}-"
                        + "${{ matrix.commitSHA }}\" >> \"$GITHUB_ENV\"",
                null,
                null
        );
    }

    private static JobStep generateSetRepositoryOwner() {
        return new JobStep(
                "Set Repository Owner",
                null,
                null,
                null,
                "echo \"REPOSITORY_OWNER=${REPOSITORY_NAME%%/*}\" >> \"$GITHUB_ENV\"",
                null,
                Map.of("REPOSITORY_NAME", "${{ matrix.repositoryName }}")
        );
    }

    private static JobStep generateCreateRepositoryToken() {
        return new JobStep(
                "Create Repository Token",
                "create-repository-token",
                null,
                "actions/create-github-app-token@64ab729a6294000f5aa4a4bc0b73d1f32bc45510",
                null,
                Map.of(
                        "owner", "${{ env.REPOSITORY_OWNER }}",
                        "repositories", "${{ matrix.repositoryName }}",
                        "permission-contents", "write",
                        "permission-workflows", "write",
                        "client-id", "${{ vars.BUILD_PUBLISHER_APP_CLIENT_ID }}",
                        "private-key", "${{ secrets.BUILD_PUBLISHER_APP_PRIVATE_KEY }}"
                ),
                null
        );
    }

    private static JobStep generateFindReleaseArtifact() {
        return new JobStep(
                "Find Release Artifact",
                "find-release-artifact",
                null,
                "$/.github/actions/find-release-artifact",
                null,
                Map.of(
                        "repository-name", "${{ matrix.repositoryName }}",
                        "release-name", "${{ env.RELEASE_NAME }}",
                        "github-token", "${{ steps.create-repository-token.outputs.token }}"
                ),
                null
        );
    }

    private static JobStep generateCheckout() {
        return new JobStep(
                "Check Out ${{ matrix.repositoryName }}",
                null,
                "steps.find-release-artifact.outputs.found != 'true'",
                "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
                null,
                Map.of(
                        "repository", "${{ matrix.repositoryName }}",
                        "ref", "${{ matrix.commitSHA }}",
                        "persist-credentials", "false",
                        "token", "${{ steps.create-repository-token.outputs.token }}"
                ),
                null
        );
    }

    private static JobStep generateBuild() {
        return new JobStep(
                "Build ${{ matrix.repositoryName }}",
                null,
                "steps.find-release-artifact.outputs.found != 'true' && matrix.buildSystem == 'universal'",
                "$/.github/actions/universal-build",
                null,
                null,
                null
        );
    }

    private static JobStep generatePublishReleaseArtifact() {
        return new JobStep(
                "Publish Release Artifact",
                null,
                "steps.find-release-artifact.outputs.found != 'true'",
                "$/.github/actions/publish-release-artifact",
                null,
                Map.of(
                        "repository-name", "${{ matrix.repositoryName }}",
                        "commit-sha", "${{ matrix.commitSHA }}",
                        "release-name", "${{ env.RELEASE_NAME }}",
                        "github-token", "${{ steps.create-repository-token.outputs.token }}"
                ),
                null
        );
    }

    private static List<BuildMatrixRow> generateRepositoryMatrixRows(
            DeclaredPipelineInput repository,
            List<RepositorySnapshot> declaredRepositorySnapshots
    ) {
        RepositorySnapshot snapshot = declaredRepositorySnapshots.stream()
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
