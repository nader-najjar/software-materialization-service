package io.materializer.shared.livepipelinedeclarationfetcher.models.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniquePipelineInputsByRepositoryAndBranchValidator.class)
public @interface UniquePipelineInputsByRepositoryAndBranch {

    String message() default "Each repository and branch pair must be unique";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
