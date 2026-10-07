package com.dec.lite.directory;

import java.util.List;
import java.util.Map;

public record DirectoryGraph(Map<String, DirectoryDefinition> directories,
                             List<ExecutionEdge> executionEdges, List<CaseEdge> caseEdges,
                             List<BackEdge> backEdges, String root) {
    public DirectoryGraph {
        directories = Map.copyOf(directories);
        executionEdges = List.copyOf(executionEdges);
        caseEdges = List.copyOf(caseEdges);
        backEdges = List.copyOf(backEdges);
        if (!directories.containsKey(root)) throw new IllegalArgumentException("Directory root is unknown: " + root);
    }
}
