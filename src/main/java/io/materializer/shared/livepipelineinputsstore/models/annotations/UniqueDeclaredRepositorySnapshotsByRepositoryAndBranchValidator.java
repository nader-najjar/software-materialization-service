package io.materializer.shared.livepipelineinputsstore.models.annotations;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.materializer.shared.models.RepositorySnapshot;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UniqueDeclaredRepositorySnapshotsByRepositoryAndBranchValidator
        implements ConstraintValidator<
                UniqueDeclaredRepositorySnapshotsByRepositoryAndBranch,
                List<RepositorySnapshot>
        > {

    @Override
    public boolean isValid(List<RepositorySnapshot> repositories, ConstraintValidatorContext context) {
        if (repositories == null) { return true; }

        Set<RepositoryBranch> seen = new HashSet<>();
        for (RepositorySnapshot snapshot : repositories) {
            if (snapshot == null || snapshot.repositoryName() == null || snapshot.branch() == null) { continue; }

            String repositoryName = snapshot.repositoryName().toLowerCase(Locale.ROOT);
            if (!seen.add(new RepositoryBranch(repositoryName, snapshot.branch()))) { return false; }
        }

        return true;
    }

    private record RepositoryBranch(String repositoryName, String branch) { }
}
