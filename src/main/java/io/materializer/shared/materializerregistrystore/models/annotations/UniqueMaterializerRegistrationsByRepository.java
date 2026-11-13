package io.materializer.shared.materializerregistrystore.models.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueMaterializerRegistrationsByRepositoryValidator.class)
public @interface UniqueMaterializerRegistrationsByRepository {

    String message() default "Materializer repositories must be unique regardless of case";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
