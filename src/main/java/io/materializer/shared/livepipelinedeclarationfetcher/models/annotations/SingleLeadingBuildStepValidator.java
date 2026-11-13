package io.materializer.shared.livepipelinedeclarationfetcher.models.annotations;

import java.util.List;

import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SingleLeadingBuildStepValidator
        implements ConstraintValidator<SingleLeadingBuildStep, List<DeclaredPipelineStep>> {

    @Override
    public boolean isValid(List<DeclaredPipelineStep> steps, ConstraintValidatorContext context) {
        if (steps == null) { return true; }
        if (steps.isEmpty()) { return false; }

        for (int index = 0; index < steps.size(); index++) {
            DeclaredPipelineStep step = steps.get(index);
            if (step == null) { continue; }
            boolean first = index == 0;
            if ("build".equals(step.operation()) != first) {
                return false;
            }
        }
        return true;
    }
}
