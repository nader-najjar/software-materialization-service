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
    private final RepositorySnapshot service = new RepositorySnapshot("owner/service", "stable", "b".repeat(40), 42);
    private final RepositorySnapshot source = new RepositorySnapshot("owner/source", "release", "c".repeat(40), 42);

    @Test
    void preservesAllRepositorySnapshotsThroughJSONRoundTrip() throws IOException {
        PipelineStructure original = new PipelineStructure(materializer, service, List.of(source));
        String json = JsonMapperUtility.MAPPER.writeValueAsString(original);
        PipelineStructure restored = JsonMapperUtility.MAPPER.readValue(json, PipelineStructure.class);
        HibernateValidatorUtility.validate(restored);

        var tree = JsonMapperUtility.MAPPER.readTree(json);
        assertEquals(materializer.repositoryName(), tree.at("/materializerRepositorySnapshot/repositoryName").asText());
        assertEquals(service.repositoryName(), tree.at("/serviceRepositorySnapshot/repositoryName").asText());
        assertEquals(source.repositoryName(), tree.at("/sourceSnapshots/0/repositoryName").asText());
        assertEquals(original, restored);
        assertEquals("c".repeat(40), restored.sourceSnapshots().getFirst().commitSHA());
    }

    @Test
    void freezesRepositoriesWithoutLosingOrder() {
        List<RepositorySnapshot> sourceSnapshots = new ArrayList<>();
        sourceSnapshots.add(source);
        sourceSnapshots.add(materializer);
        PipelineStructure snapshot = new PipelineStructure(materializer, service, sourceSnapshots);
        sourceSnapshots.clear();

        assertEquals(List.of(source, materializer), snapshot.sourceSnapshots());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.sourceSnapshots().clear());
    }

    @Test
    void validatesRequiredSnapshotFields() {
        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new PipelineStructure(null, service, List.of()))
        );

        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new PipelineStructure(materializer, null, List.of()))
        );

        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new PipelineStructure(materializer, service, null))
        );
    }

    @Test
    void rejectsInvalidNestedRepositorySnapshots() {
        RepositorySnapshot invalid = new RepositorySnapshot("missing-owner", "main", "main", 42);
        PipelineStructure snapshot = new PipelineStructure(materializer, service, List.of(invalid));

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }

    @Test
    void rejectsNullRepositorySnapshots() {
        List<RepositorySnapshot> sourceSnapshots = new ArrayList<>();
        sourceSnapshots.add(null);
        PipelineStructure snapshot = new PipelineStructure(materializer, service, sourceSnapshots);

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }

    @Test
    void rejectsDuplicateTrackedBranches() {
        PipelineStructure snapshot = new PipelineStructure(materializer, service, List.of(source, source));

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }
}
