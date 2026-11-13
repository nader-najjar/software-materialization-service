package io.materializer.shared.materializerregistrystore.models.annotations;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UniqueMaterializerRegistrationsByRepositoryValidator
        implements ConstraintValidator<UniqueMaterializerRegistrationsByRepository, List<MaterializerRegistration>> {

    @Override
    public boolean isValid(List<MaterializerRegistration> registrations, ConstraintValidatorContext context) {
        if (registrations == null) { return true; }

        List<String> repositories = registrations.stream()
                .filter(Objects::nonNull)
                .map(MaterializerRegistration::repositoryName)
                .filter(Objects::nonNull)
                .map(repository -> repository.toLowerCase(Locale.ROOT))
                .toList();

        return repositories.stream().distinct().count() == repositories.size();
    }
}
