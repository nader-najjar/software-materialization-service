package io.materializer.livepipelineinputsadvancer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.livepipelineinputsadvancer.models.PushCandidate;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.RepositoryPushHistory;
import io.materializer.shared.repositorypushhistory.models.RepositoryPush;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public class LivePipelineInputsAdvancer {

    private final RepositoryPushHistory repositoryPushHistory;
    private final LivePipelineInputsStore livePipelineInputsStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared dependencies")
    @Inject
    public LivePipelineInputsAdvancer(
            RepositoryPushHistory repositoryPushHistory,
            LivePipelineInputsStore livePipelineInputsStore
    ) {
        this.repositoryPushHistory = repositoryPushHistory;
        this.livePipelineInputsStore = livePipelineInputsStore;
    }

    public Optional<LivePipelineInputs> advance(String materializerRepositoryName) {
        Optional<LivePipelineInputs> livePipelineInputs = livePipelineInputsStore.read(materializerRepositoryName);
        if (livePipelineInputs.isEmpty()) {
            return Optional.of(initialize(materializerRepositoryName));

        } else {
            return advanceOnePush(livePipelineInputs.get());
        }
    }

    private LivePipelineInputs initialize(String materializerRepositoryName) {
        RepositorySnapshot materializerSnapshot = repositoryPushHistory.latestOnDefaultBranch(
                materializerRepositoryName
        );
        LivePipelineInputs inputs = new LivePipelineInputs(materializerSnapshot, List.of());
        HibernateValidatorUtility.validate(inputs);
        livePipelineInputsStore.write(inputs);
        return inputs;
    }

    private Optional<LivePipelineInputs> advanceOnePush(LivePipelineInputs livePipelineInputs) {
        Optional<PushCandidate> candidate = findOldestPushCandidate(livePipelineInputs);
        if (candidate.isEmpty()) { return Optional.empty(); }

        LivePipelineInputs nextInputs = applyPush(livePipelineInputs, candidate.get());
        HibernateValidatorUtility.validate(nextInputs);
        livePipelineInputsStore.write(nextInputs);
        return Optional.of(nextInputs);
    }

    Optional<PushCandidate> findOldestPushCandidate(LivePipelineInputs livePipelineInputs) {
        List<PushCandidate> candidates = new ArrayList<>();

        RepositorySnapshot materializerSnapshot = livePipelineInputs.materializerRepositorySnapshot();
        List<PushCandidate> materializerCandidates = fetchCandidates(materializerSnapshot);
        candidates.addAll(materializerCandidates);

        for (RepositorySnapshot repositorySnapshot : livePipelineInputs.declaredRepositorySnapshots()) {
            candidates.addAll(fetchCandidates(repositorySnapshot));
        }

        Comparator<RepositoryPush> pushOrder = Comparator
                .comparing((RepositoryPush push) -> Instant.parse(push.pushedAt()))
                .thenComparingLong(RepositoryPush::pushID);

        return candidates.stream()
                .min(Comparator.comparing(PushCandidate::push, pushOrder));
    }

    private LivePipelineInputs applyPush(LivePipelineInputs inputs, PushCandidate candidate) {
        RepositorySnapshot updatedSnapshot = new RepositorySnapshot(
                candidate.repositoryName(),
                candidate.branch(),
                candidate.push().after(),
                candidate.push().pushID()
        );

        RepositorySnapshot materializer = inputs.materializerRepositorySnapshot();
        if (candidateIsForSnapshot(candidate, materializer)) {
            materializer = updatedSnapshot;
        }

        List<RepositorySnapshot> declaredRepositorySnapshots = new ArrayList<>();
        for (RepositorySnapshot snapshot : inputs.declaredRepositorySnapshots()) {
            if (candidateIsForSnapshot(candidate, snapshot)) {
                snapshot = updatedSnapshot;
            }
            declaredRepositorySnapshots.add(snapshot);
        }

        return new LivePipelineInputs(materializer, declaredRepositorySnapshots);
    }

    private List<PushCandidate> fetchCandidates(RepositorySnapshot repositorySnapshot) {
        List<PushCandidate> candidates = new ArrayList<>();

        for (RepositoryPush push : repositoryPushHistory.since(repositorySnapshot)) {
            PushCandidate candidate = new PushCandidate(
                    repositorySnapshot.repositoryName(),
                    repositorySnapshot.branch(),
                    push
            );
            candidates.add(candidate);
        }

        return candidates;
    }

    private boolean candidateIsForSnapshot(PushCandidate candidate, RepositorySnapshot snapshot) {
        String snapshotRepositoryName = snapshot.repositoryName();
        String candidateRepositoryName = candidate.repositoryName();
        String snapshotBranch = snapshot.branch();
        String candidateBranch = candidate.branch();

        boolean sameRepository = snapshotRepositoryName.equalsIgnoreCase(candidateRepositoryName);
        boolean sameBranch = snapshotBranch.equals(candidateBranch);
        return sameRepository && sameBranch;
    }
}
