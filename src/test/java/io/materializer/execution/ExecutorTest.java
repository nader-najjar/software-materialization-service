package io.materializer.execution;

import java.io.IOException;
import java.util.List;

import io.materializer.execution.models.ApplicationInput;
import io.materializer.pipelinestructureplanner.PipelineStructurePlanner;
import io.materializer.shared.models.PipelineStructure;
import io.materializer.shared.utilities.JsonMapperUtility;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutorTest {

    private final PipelineStructurePlanner pipelineStructurePlanner = mock(PipelineStructurePlanner.class);
    private final Executor executor = new Executor(pipelineStructurePlanner);

    @Test
    void rejectsMissingArguments() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(null));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(new String[0]));
    }

    @Test
    void rejectsInvalidInputBeforeDispatch() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(new String[0]));
        verifyNoInteractions(pipelineStructurePlanner);
    }

    @Test
    void routesMaterializeNextToItsOperation() throws IOException {
        String json = JsonMapperUtility.MAPPER.writeValueAsString(
                new ApplicationInput("materialize-next")
        );

        executor.execute(new String[] {json});

        verify(pipelineStructurePlanner).planNextStructures();
    }

    @Test
    void rejectsInvalidPlannedStructure() {
        PipelineStructure invalidStructure = new PipelineStructure(null, null, List.of());
        when(pipelineStructurePlanner.planNextStructures()).thenReturn(List.of(invalidStructure));

        String input = "{\"operation\":\"materialize-next\"}";
        assertThrows(ValidationException.class, () -> executor.execute(new String[] {input}));
    }

    @Test
    void rejectsUnknownOperationsWithoutRunningMaterializeNext() throws IOException {
        String json = JsonMapperUtility.MAPPER.writeValueAsString(
                new ApplicationInput("unknown")
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(new String[] {json})
        );

        assertEquals("Unsupported operation: unknown", exception.getMessage());
        verifyNoInteractions(pipelineStructurePlanner);
    }
}
