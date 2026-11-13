package io.materializer.shared.materializerregistrystore.models;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterializerRegistryTest {

    @Test
    void freezesRegistryEntriesAgainstCallerMutation() {
        MaterializerRegistration registration = new MaterializerRegistration("owner/pipeline");
        List<MaterializerRegistration> registrations = new ArrayList<>(List.of(registration));
        MaterializerRegistry registry = new MaterializerRegistry(registrations);
        registrations.clear();

        assertEquals(List.of(registration), registry.registrations());
        assertThrows(UnsupportedOperationException.class, () -> registry.registrations().clear());
    }
}
