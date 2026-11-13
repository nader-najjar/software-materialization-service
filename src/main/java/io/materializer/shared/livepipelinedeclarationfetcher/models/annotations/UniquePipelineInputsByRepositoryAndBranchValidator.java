package io.materializer.shared.livepipelinedeclarationfetcher.models.annotations;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UniquePipelineInputsByRepositoryAndBranchValidator
        implements ConstraintValidator<UniquePipelineInputsByRepositoryAndBranch, List<DeclaredPipelineInput>> {

    @Override
    public boolean isValid(List<DeclaredPipelineInput> repositories, ConstraintValidatorContext context) {
        if (repositories == null) { return true; }

        Set<RepositoryBranch> seen = new HashSet<>();
        for (DeclaredPipelineInput input : repositories) {
            if (input == null || input.repositoryName() == null || input.branch() == null) { continue; }

            String repositoryName = input.repositoryName().toLowerCase(Locale.ROOT);
            if (!seen.add(new RepositoryBranch(repositoryName, input.branch()))) { return false; }
        }

        return true;
    }

    private record RepositoryBranch(String repositoryName, String branch) { }
}
