package com.dec.lite.directory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plans a unique forward path without treating cases as prerequisites. */
public final class PathPlanner {
    public PathPlan plan(DirectoryGraph graph, String from, String target) {
        String start = from == null ? graph.root() : from;
        if (!graph.directories().containsKey(start) || !graph.directories().containsKey(target)) throw new DirectoryExecutionException("unknown Directory in path: " + start + " -> " + target);
        Map<String, List<Step>> next = new HashMap<>();
        for (ExecutionEdge edge : graph.executionEdges()) next.computeIfAbsent(edge.child(), ignored -> new ArrayList<>()).add(new Step(edge.parent(), "execution"));
        for (CaseEdge edge : graph.caseEdges()) next.computeIfAbsent(edge.parent(), ignored -> new ArrayList<>()).add(new Step(edge.target(), "case"));
        List<PathPlan> paths = new ArrayList<>();
        search(start, target, next, new ArrayList<>(List.of(start)), new ArrayList<>(), paths);
        if (paths.isEmpty()) throw new DirectoryExecutionException("no Directory path: " + start + " -> " + target);
        if (paths.size() != 1) throw new DirectoryExecutionException("ambiguous Directory path: " + start + " -> " + target);
        return paths.get(0);
    }
    private static void search(String current, String target, Map<String, List<Step>> next,
                               List<String> nodes, List<String> kinds, List<PathPlan> found) {
        if (found.size() > 1) return;
        if (current.equals(target)) { found.add(new PathPlan(nodes.get(0), target, nodes, kinds)); return; }
        for (Step step : next.getOrDefault(current, List.of())) {
            if (nodes.contains(step.target())) continue;
            nodes.add(step.target()); kinds.add(step.kind());
            search(step.target(), target, next, nodes, kinds, found);
            nodes.remove(nodes.size() - 1); kinds.remove(kinds.size() - 1);
        }
    }
    private record Step(String target, String kind) { }
}
