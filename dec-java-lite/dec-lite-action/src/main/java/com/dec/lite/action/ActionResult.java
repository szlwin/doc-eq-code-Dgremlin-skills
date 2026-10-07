package com.dec.lite.action;

import com.dec.lite.information.MutationSet;
import com.dec.lite.runtime.RuntimeErrorCode;

import java.util.List;
import java.util.Map;

public record ActionResult(ActionStatus status, Map<String, Object> producedData, MutationSet mutations,
                           Map<String, Object> payload, List<String> diagnostics, String error,
                           List<String> evidence, List<ActionTraceEvent> trace, RuntimeErrorCode failureCode) {
    public ActionResult {
        if (status == null) throw new IllegalArgumentException("Action status is required");
        producedData = Map.copyOf(producedData == null ? Map.of() : producedData);
        mutations = mutations == null ? new MutationSet() : mutations;
        payload = Map.copyOf(payload == null ? Map.of() : payload);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        trace = List.copyOf(trace == null ? List.of() : trace);
        if (status == ActionStatus.FAILED && (error == null || error.isBlank())) throw new IllegalArgumentException("failed Action requires error");
        if (status != ActionStatus.FAILED && failureCode != null) throw new IllegalArgumentException("only failed Action has failureCode");
    }
    public static ActionResult success(Map<String, Object> produced, MutationSet mutations, List<String> evidence, List<ActionTraceEvent> trace) {
        return new ActionResult(ActionStatus.SUCCESS, produced, mutations, Map.of(), List.of(), null, evidence, trace, null);
    }
    public static ActionResult failed(String error, MutationSet mutations, List<String> diagnostics, List<ActionTraceEvent> trace) {
        return failed(error, mutations, diagnostics, trace, RuntimeErrorCode.ACTION);
    }
    public static ActionResult failed(String error, MutationSet mutations, List<String> diagnostics, List<ActionTraceEvent> trace, RuntimeErrorCode code) {
        return new ActionResult(ActionStatus.FAILED, Map.of(), mutations, Map.of(), diagnostics, error, List.of(), trace, code);
    }
    public static ActionResult skipped(String reason) {
        return new ActionResult(ActionStatus.SKIPPED, Map.of(), new MutationSet(), Map.of(), List.of(reason), null, List.of(), List.of(), null);
    }
}
