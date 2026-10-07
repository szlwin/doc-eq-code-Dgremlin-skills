package com.dec.lite.action;

import com.dec.lite.information.InformationKey;
import com.dec.lite.information.RecognitionResult;

import java.util.ArrayList;
import java.util.List;

/** Ordered fail-fast Action pipeline with one shared model/Information context. */
public final class ActionPipeline {
    private final ActionRuntime runtime;
    public ActionPipeline(ActionRuntime runtime) { this.runtime = runtime; }

    public List<ActionResult> execute(List<ActionDefinition> actions, ActionExecutionContext context) {
        List<ActionResult> results = new ArrayList<>();
        String directory = null;
        InformationKey target = null;
        for (ActionDefinition action : actions) {
            if (directory != null && !directory.equals(action.ownerDirectory())) {
                verifyTarget(directory, target, context);
            }
            directory = action.ownerDirectory();
            target = action.targetInformation();
            ActionResult result = runtime.execute(action, context);
            results.add(result);
            if (result.status() != ActionStatus.SUCCESS) return List.copyOf(results);
        }
        if (directory != null) verifyTarget(directory, target, context);
        return List.copyOf(results);
    }

    private static void verifyTarget(String directory, InformationKey target, ActionExecutionContext context) {
        if (target == null) return;
        RecognitionResult result = context.informationEngine().evaluate(target, context.model());
        if (result.status() != RecognitionResult.Status.TRUE) {
            throw new ActionExecutionException("Directory Information is not TRUE after actions: " + directory + " -> " + target + " (" + result.status() + ")");
        }
    }
}
