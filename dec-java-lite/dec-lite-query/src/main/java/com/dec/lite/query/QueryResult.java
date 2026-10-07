package com.dec.lite.query;

import com.dec.lite.information.ModelContext;

import java.util.List;

public record QueryResult(List<ModelContext> models, int scannedCandidates, List<String> trace) {
    public QueryResult { models = List.copyOf(models); trace = List.copyOf(trace); }
}
