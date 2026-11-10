package io.materializer.shared.models;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineStructureTest {

    private final RepositorySnapshot materializer = new RepositorySnapshot(
            "owner/materializer",
            "main",
            "a".repeat(40),
            42
    );
    private final RepositorySnapshot source = new RepositorySnapshot("owner/source", "release", "c".repeat(40), 42);

    @Test
    void preservesAllRepositorySnapshotsThroughJSONRoundTrip() throws IOException {
        PipelineStructure original = new PipelineStructure(materializer, List.of(source), false);
        String json = JsonMapperUtility.MAPPER.writeValueAsString(original);
        PipelineStructure restored = JsonMapperUtility.MAPPER.readValue(json, PipelineStructure.class);
        HibernateValidatorUtility.validate(restored);

        var tree = JsonMapperUtility.MAPPER.readTree(json);
        assertEquals(materializer.repositoryName(), tree.at("/materializerRepositorySnapshot/repositoryName").asText());
        assertEquals(source.repositoryName(), tree.at("/sourceSnapshots/0/repositoryName").asText());
        assertEquals(false, tree.required("dispatched").booleanValue());
        assertEquals(original, restored);
        assertEquals("c".repeat(40), restored.sourceSnapshots().getFirst().commitSHA());
    }

    @Test
    void preservesAcceptedDispatchThroughJSONRoundTrip() throws IOException {
        PipelineStructure dispatchedStructure = new PipelineStructure(materializer, List.of(source), true);
        String json = JsonMapperUtility.MAPPER.writeValueAsString(dispatchedStructure);

        assertEquals(dispatchedStructure, JsonMapperUtility.MAPPER.readValue(json, PipelineStructure.class));
    }

    @Test
    void freezesRepositoriesWithoutLosingOrder() {
        List<RepositorySnapshot> sourceSnapshots = new ArrayList<>();
        sourceSnapshots.add(source);
        sourceSnapshots.add(materializer);
        PipelineStructure snapshot = new PipelineStructure(materializer, sourceSnapshots, false);
        sourceSnapshots.clear();

        assertEquals(List.of(source, materializer), snapshot.sourceSnapshots());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.sourceSnapshots().clear());
    }

    @Test
    void validatesRequiredSnapshotFields() {
        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new PipelineStructure(null, List.of(), false))
        );

        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new PipelineStructure(materializer, null, false))
        );
    }

    @Test
    void rejectsInvalidNestedRepositorySnapshots() {
        RepositorySnapshot invalid = new RepositorySnapshot("missing-owner", "main", "main", 42);
        PipelineStructure snapshot = new PipelineStructure(materializer, List.of(invalid), false);

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }

    @Test
    void rejectsNullRepositorySnapshots() {
        List<RepositorySnapshot> sourceSnapshots = new ArrayList<>();
        sourceSnapshots.add(null);
        PipelineStructure snapshot = new PipelineStructure(materializer, sourceSnapshots, false);

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }

    @Test
    void rejectsDuplicateTrackedBranches() {
        PipelineStructure snapshot = new PipelineStructure(materializer, List.of(source, source), false);

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }
}
