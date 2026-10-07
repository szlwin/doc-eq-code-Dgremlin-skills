package com.dec.lite.information;

import java.util.List;
import java.util.Objects;

/** Immutable Information recognition result. UNRESOLVED is distinct from FALSE. */
public record RecognitionResult(
        Status status,
        List<String> evidence,
        List<InformationKey> dependencies,
        List<String> errors,
        String modelVersion,
        List<TraceEvent> trace) {
    public RecognitionResult(Status status, List<String> evidence, List<InformationKey> dependencies,
                             List<String> errors, String modelVersion) {
        this(status, evidence, dependencies, errors, modelVersion, List.of());
    }
    public enum Status { TRUE, FALSE, UNRESOLVED, ERROR }

    public RecognitionResult {
        status = Objects.requireNonNull(status, "status");
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        errors = List.copyOf(errors == null ? List.of() : errors);
        trace = List.copyOf(trace == null ? List.of() : trace);
    }

    public static RecognitionResult of(Status status, String modelVersion, String... evidence) {
        return new RecognitionResult(status, List.of(evidence), List.of(), List.of(), modelVersion);
    }

    public static RecognitionResult error(String modelVersion, String message) {
        return new RecognitionResult(Status.ERROR, List.of(), List.of(), List.of(message), modelVersion);
    }

    public static RecognitionResult unresolved(String modelVersion, String message) {
        return new RecognitionResult(Status.UNRESOLVED, List.of(), List.of(), List.of(message), modelVersion);
    }

    public boolean isTerminal() { return status == Status.TRUE || status == Status.FALSE; }

    public RecognitionResult withTrace(TraceEvent event) {
        java.util.ArrayList<TraceEvent> values = new java.util.ArrayList<>(trace);
        values.add(event);
        return new RecognitionResult(status, evidence, dependencies, errors, modelVersion, values);
    }
}
