package io.materializer.livepipelineinputsreconciler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.RepositoryPushHistory;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public class LivePipelineInputsReconciler {

    private final LivePipelineDeclarationFetcher livePipelineDeclarationFetcher;

    private final RepositoryPushHistory repositoryPushHistory;

    private final LivePipelineInputsStore livePipelineInputsStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared inputs store")
    @Inject
    public LivePipelineInputsReconciler(
            LivePipelineDeclarationFetcher livePipelineDeclarationFetcher,
            RepositoryPushHistory repositoryPushHistory,
            LivePipelineInputsStore livePipelineInputsStore
    ) {
        this.livePipelineDeclarationFetcher = livePipelineDeclarationFetcher;
        this.repositoryPushHistory = repositoryPushHistory;
        this.livePipelineInputsStore = livePipelineInputsStore;
    }

    public LivePipelineInputs reconcile(LivePipelineInputs inputs) {
        RepositorySnapshot materializerSnapshot = inputs.materializerRepositorySnapshot();
        LivePipelineDeclaration declaration = livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot);

        List<RepositorySnapshot> declaredRepositorySnapshots = pinDeclaredRepositoriesReusingPreviousSnapshots(
                declaration.repositories(),
                inputs.declaredRepositorySnapshots()
        );

        LivePipelineInputs reconciledInputs = new LivePipelineInputs(materializerSnapshot, declaredRepositorySnapshots);
        HibernateValidatorUtility.validate(reconciledInputs);
        livePipelineInputsStore.write(reconciledInputs);
        return reconciledInputs;
    }

    private List<RepositorySnapshot> pinDeclaredRepositoriesReusingPreviousSnapshots(
            List<DeclaredPipelineInput> declaredInputs,
            List<RepositorySnapshot> previousSnapshots
    ) {
        List<RepositorySnapshot> declaredRepositorySnapshots = new ArrayList<>();
        for (DeclaredPipelineInput declaredInput : declaredInputs) {
            RepositorySnapshot snapshot = retainOrFetchSnapshot(declaredInput, previousSnapshots);
            declaredRepositorySnapshots.add(snapshot);
        }
        return declaredRepositorySnapshots;
    }

    private RepositorySnapshot retainOrFetchSnapshot(
            DeclaredPipelineInput declaredInput,
            List<RepositorySnapshot> previousSnapshots
    ) {
        Optional<RepositorySnapshot> previousSnapshot = findPreviousSnapshot(declaredInput, previousSnapshots);
        if (previousSnapshot.isPresent()) { return previousSnapshot.get(); }

        return repositoryPushHistory.latest(declaredInput.repositoryName(), declaredInput.branch());
    }

    private Optional<RepositorySnapshot> findPreviousSnapshot(
            DeclaredPipelineInput declaredInput,
            List<RepositorySnapshot> previousSnapshots
    ) {
        for (RepositorySnapshot snapshot : previousSnapshots) {
            boolean sameRepository = snapshot.repositoryName().equalsIgnoreCase(declaredInput.repositoryName());
            boolean sameBranch = snapshot.branch().equals(declaredInput.branch());
            if (sameRepository && sameBranch) { return Optional.of(snapshot); }
        }
        return Optional.empty();
    }

}
