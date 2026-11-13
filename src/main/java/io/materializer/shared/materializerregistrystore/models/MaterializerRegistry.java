package io.materializer.shared.materializerregistrystore.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.materializerregistrystore.models.annotations.UniqueMaterializerRegistrationsByRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MaterializerRegistry(

        @JsonProperty("registrations")
        @NotNull
        @UniqueMaterializerRegistrationsByRepository
        List<@NotNull @Valid MaterializerRegistration> registrations

) {
    public MaterializerRegistry {
        registrations = registrations == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(registrations));
    }
}
