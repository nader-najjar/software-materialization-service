package io.materializer.execution;

import com.google.inject.Inject;
import io.materializer.execution.models.ApplicationInput;
import io.materializer.shared.utilities.JsonSanitizer;

public class Executor {

    private final RegisterExecutor registerExecutor;
    private final MaterializeNewPushesExecutor materializeNewPushesExecutor;

    @Inject
    public Executor(
            RegisterExecutor registerExecutor,
            MaterializeNewPushesExecutor materializeNewPushesExecutor
    ) {
        this.registerExecutor = registerExecutor;
        this.materializeNewPushesExecutor = materializeNewPushesExecutor;
    }

    public void execute(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("No input provided");
        }

        ApplicationInput input = JsonSanitizer.parseAndValidate(args[0], ApplicationInput.class);
        switch (input.operation()) {
            case "register" -> registerExecutor.execute(input.repositoryName());
            case "materialize-new-pushes" -> materializeNewPushesExecutor.execute();
            default -> throw new IllegalArgumentException("Unsupported operation: " + input.operation());
        }
    }

}
