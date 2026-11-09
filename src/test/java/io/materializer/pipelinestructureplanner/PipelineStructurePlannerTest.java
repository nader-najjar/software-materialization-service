package io.materializer.pipelinestructureplanner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.pipelinestructureplanner.models.MaterializerRegistration;
import io.materializer.pipelinestructureplanner.models.MaterializerRegistry;
import io.materializer.pipelinestructureplanner.models.RepositoryPush;
import io.materializer.shared.PipelineStructureStore;
import io.materializer.shared.models.PipelineStructure;
import io.materializer.shared.models.RepositorySnapshot;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PipelineStructurePlannerTest {

    private final MaterializerRegistryFetcher materializerRegistryFetcher = mock(MaterializerRegistryFetcher.class);
    private final PipelineStructureStore pipelineStructureStore = mock(PipelineStructureStore.class);
    private final GitHub gitHub = mock(GitHub.class);
    private final PushHistoryFetcher pushHistoryFetcher = mock(PushHistoryFetcher.class);
    private final PipelineStructurePlanner pipelineStructurePlanner = new PipelineStructurePlanner(
            materializerRegistryFetcher,
            pipelineStructureStore,
            new DeclaredPipelineInputsFetcher(gitHub),
            pushHistoryFetcher,
            new EnvironmentVariables("test", "owner/registry", "owner/storage", "owner/service")
    );

    @Test
    void readsPipelineJsonAtTheResolvedDefaultBranchCommit() throws IOException {
        registerMaterializer();
        GHRepository repository = mock(GHRepository.class);
        String commitSHA = "a".repeat(40);
        when(gitHub.getRepository("owner/one")).thenReturn(repository);
        when(pushHistoryFetcher.latestOnDefaultBranch("owner/one"))
                .thenReturn(new RepositorySnapshot("owner/one", "trunk", commitSHA, 42));
        GHContent file = mock(GHContent.class);
        String pipelineJson = "{\"repositories\":[]}";
        when(repository.getFileContent("pipeline.json", commitSHA)).thenReturn(file);
        when(file.read()).thenReturn(new ByteArrayInputStream(pipelineJson.getBytes(StandardCharsets.UTF_8)));

        RepositorySnapshot servicePush = new RepositorySnapshot("owner/service", "stable", "d".repeat(40), 107);
        when(pushHistoryFetcher.latestOnDefaultBranch("owner/service")).thenReturn(servicePush);

        List<PipelineStructure> snapshots = pipelineStructurePlanner.planNextStructures();
        assertEquals(1, snapshots.size());
        PipelineStructure snapshot = snapshots.getFirst();

        assertEquals(
                new RepositorySnapshot("owner/one", "trunk", commitSHA, 42),
                snapshot.materializerRepositorySnapshot()
        );
        assertEquals(servicePush, snapshot.serviceRepositorySnapshot());
        assertEquals(List.of(), snapshot.sourceSnapshots());
    }

    @Test
    void returnsNoSnapshotsForAnEmptyRegistry() {
        when(materializerRegistryFetcher.fetch()).thenReturn(new MaterializerRegistry(List.of()));

        assertEquals(List.of(), pipelineStructurePlanner.planNextStructures());
        verifyNoInteractions(pipelineStructureStore);
    }

    @Test
    void scansStoredPinsAndReadsInputsOnlyForASelectedMaterializerPush() throws IOException {
        registerMaterializer();
        RepositorySnapshot materializer = new RepositorySnapshot("owner/one", "trunk", "a".repeat(40), 42);
        RepositorySnapshot service = new RepositorySnapshot("owner/service", "stable", "b".repeat(40), 42);
        RepositorySnapshot source = new RepositorySnapshot("owner/library", "release", "c".repeat(40), 107);
        RepositorySnapshot api = new RepositorySnapshot("owner/api", "main", "f".repeat(40), 106);
        PipelineStructure snapshot = new PipelineStructure(materializer, service, List.of(source, api));
        when(pipelineStructureStore.read("owner/one")).thenReturn(Optional.of(snapshot));

        assertEquals(List.of(), pipelineStructurePlanner.planNextStructures());

        InOrder lookups = inOrder(pushHistoryFetcher);
        lookups.verify(pushHistoryFetcher).since(service);
        lookups.verify(pushHistoryFetcher).since(materializer);
        lookups.verify(pushHistoryFetcher).since(source);
        lookups.verify(pushHistoryFetcher).since(api);

        when(pushHistoryFetcher.since(service)).thenReturn(List.of(push(11, 20)));
        when(pushHistoryFetcher.since(materializer)).thenReturn(List.of(push(10, 10)));
        RepositoryPush sourcePush = new RepositoryPush(
                9,
                source.commitSHA(),
                "a".repeat(40),
                Instant.ofEpochSecond(10)
        );
        when(pushHistoryFetcher.since(source)).thenReturn(List.of(sourcePush));
        RepositorySnapshot nextSource = new RepositorySnapshot(
                source.repositoryName(),
                source.branch(),
                sourcePush.after(),
                sourcePush.pushID()
        );
        PipelineStructure expectedSourceUpdate = new PipelineStructure(
                materializer,
                service,
                List.of(nextSource, api)
        );
        assertEquals(List.of(expectedSourceUpdate), pipelineStructurePlanner.planNextStructures());

        String nextSHA = "e".repeat(40);
        RepositoryPush servicePush = new RepositoryPush(108, service.commitSHA(), nextSHA, Instant.ofEpochSecond(5));
        when(pushHistoryFetcher.since(service)).thenReturn(List.of(servicePush));
        RepositorySnapshot nextService = new RepositorySnapshot(
                service.repositoryName(),
                service.branch(),
                nextSHA,
                servicePush.pushID()
        );
        PipelineStructure expected = new PipelineStructure(
                materializer,
                nextService,
                snapshot.sourceSnapshots()
        );
        assertEquals(List.of(expected), pipelineStructurePlanner.planNextStructures());

        RepositoryPush matPush = new RepositoryPush(109, materializer.commitSHA(), nextSHA, Instant.EPOCH);
        when(pushHistoryFetcher.since(materializer)).thenReturn(List.of(matPush));
        verifyNoInteractions(gitHub);
        GHRepository repository = mock(GHRepository.class);
        when(gitHub.getRepository(materializer.repositoryName())).thenReturn(repository);
        String nextConfig = """
                {"steps":[],"repositories":[{"repositoryName":"owner/library","branch":"release",
                  "build_system":"universal","platforms":["x86_64-linux"]},
                 {"repositoryName":"owner/new-api","branch":"main",
                  "build_system":"universal","platforms":["x86_64-linux"]}]}
                """;
        GHContent nextFile = mock(GHContent.class);
        when(repository.getFileContent("pipeline.json", nextSHA)).thenReturn(nextFile);
        when(nextFile.read()).thenReturn(
                new ByteArrayInputStream(nextConfig.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))
        );
        RepositorySnapshot added = new RepositorySnapshot("owner/new-api", "main", "d".repeat(40), 110);
        when(pushHistoryFetcher.latest("owner/new-api", "main")).thenReturn(added);
        RepositorySnapshot nextMaterializer = new RepositorySnapshot(
                materializer.repositoryName(),
                materializer.branch(),
                nextSHA,
                109
        );
        PipelineStructure matUpdate = new PipelineStructure(
                nextMaterializer,
                service,
                List.of(source, added)
        );
        assertEquals(List.of(matUpdate), pipelineStructurePlanner.planNextStructures());
        assertThrows(IllegalArgumentException.class, pipelineStructurePlanner::planNextStructures);
    }

    @Test
    void propagatesRegistryFailures() {
        IllegalArgumentException failure = new IllegalArgumentException("Invalid registry");
        when(materializerRegistryFetcher.fetch()).thenThrow(failure);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                pipelineStructurePlanner::planNextStructures
        );

        assertSame(failure, exception);
        verifyNoInteractions(pipelineStructureStore);
    }

    @Test
    void pinsTheConfiguredBranchForThePipelineInput() throws IOException {
        RepositorySnapshot materializerSnapshot = new RepositorySnapshot("owner/one", "main", "a".repeat(40), 1);
        GHRepository repository = mock(GHRepository.class);
        GHContent pipelineFile = mock(GHContent.class);
        String pipelineJson = """
                {"repositories":[{"repositoryName":"owner/source","branch":"release",
                  "build_system":"universal","platforms":["x86_64-linux"]}]}
                """;
        when(gitHub.getRepository(materializerSnapshot.repositoryName())).thenReturn(repository);
        when(repository.getFileContent("pipeline.json", materializerSnapshot.commitSHA())).thenReturn(pipelineFile);
        when(pipelineFile.read()).thenReturn(new ByteArrayInputStream(pipelineJson.getBytes(StandardCharsets.UTF_8)));
        String commitSHA = "b".repeat(40);
        when(pushHistoryFetcher.latest("owner/source", "release"))
                .thenReturn(new RepositorySnapshot("owner/source", "release", commitSHA, 42));

        assertEquals(
                List.of(new RepositorySnapshot("owner/source", "release", commitSHA, 42)),
                pipelineStructurePlanner.pinInputsAtMaterializerSnapshotReusingPreviousPins(
                        materializerSnapshot,
                        List.of()
                )
        );
    }

    private RepositoryPush push(long id, long seconds) {
        return new RepositoryPush(id, "a".repeat(40), "b".repeat(40), Instant.ofEpochSecond(seconds));
    }

    private void registerMaterializer() {
        MaterializerRegistration registration = new MaterializerRegistration("owner/one");
        when(materializerRegistryFetcher.fetch()).thenReturn(new MaterializerRegistry(List.of(registration)));
    }
}
