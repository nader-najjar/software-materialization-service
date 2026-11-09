package io.materializer.pipelinestructureplanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.pipelinestructureplanner.models.DeclaredPipelineInputs;
import io.materializer.pipelinestructureplanner.models.MaterializerRegistration;
import io.materializer.pipelinestructureplanner.models.MaterializerRegistry;
import io.materializer.pipelinestructureplanner.models.PipelineInput;
import io.materializer.pipelinestructureplanner.models.PushCandidate;
import io.materializer.pipelinestructureplanner.models.RepositoryPush;
import io.materializer.pipelinestructureplanner.models.RepositoryRole;
import io.materializer.shared.PipelineStructureStore;
import io.materializer.shared.models.PipelineStructure;
import io.materializer.shared.models.RepositorySnapshot;

public class PipelineStructurePlanner {

    private final MaterializerRegistryFetcher materializerRegistryFetcher;
    private final PipelineStructureStore pipelineStructureStore;
    private final DeclaredPipelineInputsFetcher declaredPipelineInputsFetcher;
    private final PushHistoryFetcher pushHistoryFetcher;
    private final String materializationServiceRepository;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The injected store keeps no per-run state")
    @Inject
    public PipelineStructurePlanner(
            MaterializerRegistryFetcher materializerRegistryFetcher,
            PipelineStructureStore pipelineStructureStore,
            DeclaredPipelineInputsFetcher declaredPipelineInputsFetcher,
            PushHistoryFetcher pushHistoryFetcher,
            EnvironmentVariables environmentVariables
    ) {
        this.materializerRegistryFetcher = materializerRegistryFetcher;
        this.pipelineStructureStore = pipelineStructureStore;
        this.declaredPipelineInputsFetcher = declaredPipelineInputsFetcher;
        this.pushHistoryFetcher = pushHistoryFetcher;
        this.materializationServiceRepository = environmentVariables.materializationServiceRepository();
    }

    public List<PipelineStructure> planNextStructures() {
        MaterializerRegistry materializerRegistry = materializerRegistryFetcher.fetch();

        List<PipelineStructure> nextStructures = new ArrayList<>();
        for (MaterializerRegistration registration : materializerRegistry.registrations()) {
            planNextStructure(registration).ifPresent(nextStructures::add);
        }

        return nextStructures;
    }

    private Optional<PipelineStructure> planNextStructure(MaterializerRegistration materializerRegistration) {
        Optional<PipelineStructure> currentStructure = pipelineStructureStore.read(
                materializerRegistration.repositoryName()
        );

        if (currentStructure.isEmpty()) {
            return Optional.of(initialize(materializerRegistration));

        } else {
            return advance(currentStructure.get());
        }
    }

    private PipelineStructure initialize(MaterializerRegistration materializerRegistration) {
        String materializerRepository = materializerRegistration.repositoryName();
        RepositorySnapshot materializerSnapshot = pushHistoryFetcher.latestOnDefaultBranch(materializerRepository);
        List<RepositorySnapshot> sourceSnapshots = pinInputsAtMaterializerSnapshotReusingPreviousPins(
                materializerSnapshot,
                List.of()
        );

        RepositorySnapshot serviceSnapshot = pushHistoryFetcher.latestOnDefaultBranch(materializationServiceRepository);

        return new PipelineStructure(materializerSnapshot, serviceSnapshot, sourceSnapshots);
    }

    private Optional<PipelineStructure> advance(PipelineStructure currentStructure) {
        Optional<PushCandidate> oldestCandidate = findOldestPushCandidate(currentStructure);
        if (oldestCandidate.isEmpty()) { return Optional.empty(); }

        return Optional.of(applyPush(currentStructure, oldestCandidate.get()));
    }

    private Optional<PushCandidate> findOldestPushCandidate(PipelineStructure currentStructure) {
        RepositorySnapshot serviceSnapshot = currentStructure.serviceRepositorySnapshot();
        List<PushCandidate> serviceCandidates = fetchCandidates(serviceSnapshot, RepositoryRole.SERVICE);

        RepositorySnapshot materializerSnapshot = currentStructure.materializerRepositorySnapshot();
        List<PushCandidate> materializerCandidates = fetchCandidates(materializerSnapshot, RepositoryRole.MATERIALIZER);

        List<PushCandidate> candidates = new ArrayList<>();
        candidates.addAll(serviceCandidates);
        candidates.addAll(materializerCandidates);
        for (RepositorySnapshot sourceSnapshot : currentStructure.sourceSnapshots()) {
            List<PushCandidate> sourceCandidates = fetchCandidates(sourceSnapshot, RepositoryRole.SOURCE);
            candidates.addAll(sourceCandidates);
        }

        Comparator<RepositoryPush> pushOrder = Comparator
                .comparing(RepositoryPush::pushedAt)
                .thenComparingLong(RepositoryPush::pushID);
        return candidates.stream()
                .min(Comparator.comparing(PushCandidate::push, pushOrder));
    }

    private PipelineStructure applyPush(PipelineStructure currentStructure, PushCandidate selectedCandidate) {
        RepositoryPush push = selectedCandidate.push();
        String branch = selectedCandidate.branch();
        String repositoryName = selectedCandidate.repositoryName();
        if (push.deletesBranch()) {
            String message = "Branch %s in %s was deleted".formatted(branch, repositoryName);
            throw new IllegalStateException(message);
        }

        RepositorySnapshot nextRepositorySnapshot = new RepositorySnapshot(
                repositoryName,
                branch,
                push.after(),
                push.pushID()
        );
        return switch (selectedCandidate.role()) {
            case SERVICE -> applyServicePush(currentStructure, nextRepositorySnapshot);
            case MATERIALIZER -> applyMaterializerPush(currentStructure, nextRepositorySnapshot);
            case SOURCE -> applySourcePush(currentStructure, nextRepositorySnapshot);
        };
    }

    private PipelineStructure applyServicePush(
            PipelineStructure currentStructure,
            RepositorySnapshot serviceSnapshot
    ) {
        return new PipelineStructure(
                currentStructure.materializerRepositorySnapshot(),
                serviceSnapshot,
                currentStructure.sourceSnapshots()
        );
    }

    private PipelineStructure applyMaterializerPush(
            PipelineStructure currentStructure,
            RepositorySnapshot materializerSnapshot
    ) {
        List<RepositorySnapshot> sourceSnapshots = pinInputsAtMaterializerSnapshotReusingPreviousPins(
                materializerSnapshot,
                currentStructure.sourceSnapshots()
        );
        return new PipelineStructure(
                materializerSnapshot,
                currentStructure.serviceRepositorySnapshot(),
                sourceSnapshots
        );
    }

    private PipelineStructure applySourcePush(
            PipelineStructure currentStructure,
            RepositorySnapshot nextSourceSnapshot
    ) {
        List<RepositorySnapshot> sourceSnapshots = new ArrayList<>(currentStructure.sourceSnapshots());
        String repositoryName = nextSourceSnapshot.repositoryName();
        String branch = nextSourceSnapshot.branch();
        int sourceIndex = IntStream.range(0, sourceSnapshots.size())
                .filter(index -> matchesBranch(sourceSnapshots.get(index), repositoryName, branch))
                .findFirst()
                .orElseThrow();

        sourceSnapshots.set(sourceIndex, nextSourceSnapshot);
        return new PipelineStructure(
                currentStructure.materializerRepositorySnapshot(),
                currentStructure.serviceRepositorySnapshot(),
                sourceSnapshots
        );
    }

    private List<PushCandidate> fetchCandidates(RepositorySnapshot repositorySnapshot, RepositoryRole role) {
        return pushHistoryFetcher.since(repositorySnapshot).stream()
                .map(push -> new PushCandidate(
                        role,
                        repositorySnapshot.repositoryName(),
                        repositorySnapshot.branch(),
                        push
                ))
                .toList();
    }

    List<RepositorySnapshot> pinInputsAtMaterializerSnapshotReusingPreviousPins(
            RepositorySnapshot materializerSnapshot,
            List<RepositorySnapshot> previousPins
    ) {
        DeclaredPipelineInputs declaredInputs = declaredPipelineInputsFetcher.fetch(materializerSnapshot);
        List<RepositorySnapshot> repositorySnapshots = new ArrayList<>();
        for (PipelineInput source : declaredInputs.repositories()) {
            RepositorySnapshot repositorySnapshot = previousPins.stream()
                    .filter(candidate -> matchesBranch(candidate, source.repositoryName(), source.branch()))
                    .findFirst()
                    .orElseGet(() -> pushHistoryFetcher.latest(source.repositoryName(), source.branch()));
            repositorySnapshots.add(repositorySnapshot);
        }

        return repositorySnapshots;
    }

    private static boolean matchesBranch(RepositorySnapshot repositorySnapshot, String repositoryName, String branch) {
        return repositorySnapshot.repositoryName().equalsIgnoreCase(repositoryName)
                && repositorySnapshot.branch().equals(branch);
    }
}
