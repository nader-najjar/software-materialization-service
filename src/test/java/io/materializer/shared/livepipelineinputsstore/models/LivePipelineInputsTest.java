package io.materializer.shared.livepipelineinputsstore.models;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LivePipelineInputsTest {

    private final RepositorySnapshot materializer = new RepositorySnapshot(
            "owner/materializer",
            "main",
            "a".repeat(40),
            42L
    );
    private final RepositorySnapshot source = new RepositorySnapshot("owner/source", "release", "c".repeat(40), 42L);

    @Test
    void preservesAllRepositorySnapshotsThroughJSONRoundTrip() throws IOException {
        LivePipelineInputs original = new LivePipelineInputs(materializer, List.of(source));
        String json = JsonMapperUtility.MAPPER.writeValueAsString(original);
        LivePipelineInputs restored = JsonMapperUtility.MAPPER.readValue(json, LivePipelineInputs.class);
        HibernateValidatorUtility.validate(restored);

        var tree = JsonMapperUtility.MAPPER.readTree(json);
        assertEquals(materializer.repositoryName(), tree.at("/materializerRepositorySnapshot/repositoryName").asText());
        assertEquals(source.repositoryName(), tree.at("/declaredRepositorySnapshots/0/repositoryName").asText());
        assertEquals(original, restored);
        assertEquals("c".repeat(40), restored.declaredRepositorySnapshots().getFirst().commitSHA());
    }

    @Test
    void freezesRepositoriesWithoutLosingOrder() {
        List<RepositorySnapshot> declaredRepositorySnapshots = new ArrayList<>();
        declaredRepositorySnapshots.add(source);
        declaredRepositorySnapshots.add(materializer);
        LivePipelineInputs snapshot = new LivePipelineInputs(materializer, declaredRepositorySnapshots);
        declaredRepositorySnapshots.clear();

        assertEquals(List.of(source, materializer), snapshot.declaredRepositorySnapshots());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.declaredRepositorySnapshots().clear());
    }

    @Test
    void validatesRequiredSnapshotFields() {
        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new LivePipelineInputs(null, List.of()))
        );

        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new LivePipelineInputs(materializer, null))
        );
    }

    @Test
    void rejectsInvalidNestedRepositorySnapshots() {
        RepositorySnapshot invalid = new RepositorySnapshot("missing-owner", "main", "main", 42L);
        LivePipelineInputs snapshot = new LivePipelineInputs(materializer, List.of(invalid));

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }

    @Test
    void rejectsNullRepositorySnapshots() {
        List<RepositorySnapshot> declaredRepositorySnapshots = new ArrayList<>();
        declaredRepositorySnapshots.add(null);
        LivePipelineInputs snapshot = new LivePipelineInputs(materializer, declaredRepositorySnapshots);

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }

    @Test
    void rejectsDuplicateTrackedBranches() {
        LivePipelineInputs snapshot = new LivePipelineInputs(materializer, List.of(source, source));

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }
}
