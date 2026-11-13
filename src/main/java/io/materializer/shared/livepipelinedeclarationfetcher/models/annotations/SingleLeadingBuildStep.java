package io.materializer.shared.livepipelinedeclarationfetcher.models.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SingleLeadingBuildStepValidator.class)
public @interface SingleLeadingBuildStep {

    String message() default "The first operation must be build and it may appear only once";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
