package io.materializer.execution;

import java.io.IOException;
import java.util.List;

import io.materializer.execution.models.ApplicationInput;
import io.materializer.pipelinestructureplanner.PipelineStructurePlanner;
import io.materializer.shared.PipelineStructureStore;
import io.materializer.shared.models.PipelineStructure;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ExecutorTest {

    private final PipelineStructurePlanner pipelineStructurePlanner = mock(PipelineStructurePlanner.class);
    private final PipelineStructureStore pipelineStructureStore = mock(PipelineStructureStore.class);
    private final WorkflowGenerator workflowGenerator = mock(WorkflowGenerator.class);
    private final WorkflowPublisher workflowPublisher = mock(WorkflowPublisher.class);
    private final Executor executor = new Executor(
            pipelineStructurePlanner,
            pipelineStructureStore,
            workflowGenerator,
            workflowPublisher
    );
    private final PipelineStructure structure = new PipelineStructure(
            new RepositorySnapshot("owner/materializer", "main", "a".repeat(40), 1),
            List.of(),
            false
    );

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

        when(pipelineStructurePlanner.planNextStructures()).thenReturn(List.of(structure));
        String workflowYaml = "name: Materialize";
        when(workflowGenerator.generate(structure)).thenReturn(workflowYaml);
        executor.execute(new String[] {json});
        InOrder order = inOrder(pipelineStructureStore, workflowGenerator, workflowPublisher);
        order.verify(pipelineStructureStore).write(structure);
        order.verify(workflowGenerator).generate(structure);

        order.verify(workflowPublisher).publish("owner/materializer", workflowYaml);
        order.verify(pipelineStructureStore).write(new PipelineStructure(
                structure.materializerRepositorySnapshot(),
                structure.sourceSnapshots(),
                true
        ));
    }

    @Test
    void leavesStructureUndispatchedWhenPublishingFails() {
        String workflowYaml = "name: Materialize";
        when(pipelineStructurePlanner.planNextStructures()).thenReturn(List.of(structure));
        when(workflowGenerator.generate(structure)).thenReturn(workflowYaml);
        doThrow(new IllegalStateException("Publish failed")).when(workflowPublisher)
                .publish("owner/materializer", workflowYaml);

        String input = "{\"operation\":\"materialize-next\"}";
        assertThrows(IllegalStateException.class, () -> executor.execute(new String[] {input}));
        verify(pipelineStructureStore).write(structure);
        verifyNoMoreInteractions(pipelineStructureStore);
    }

    @Test
    void rejectsInvalidPlannedStructure() {
        PipelineStructure invalidStructure = new PipelineStructure(null, List.of(), false);
        when(pipelineStructurePlanner.planNextStructures()).thenReturn(List.of(invalidStructure));

        String input = "{\"operation\":\"materialize-next\"}";
        assertThrows(ValidationException.class, () -> executor.execute(new String[] {input}));
        verifyNoInteractions(pipelineStructureStore, workflowGenerator, workflowPublisher);
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
