package com.dec.lite.directory;

import java.util.ArrayList;
import java.util.List;

/** Back follows case ownership first, then explicit adjacent BackEdges. */
public final class BackPlanner {
    public BackPlan plan(DirectoryGraph graph, String from, String target) {
        if (!graph.directories().containsKey(from) || !graph.directories().containsKey(target)) throw new DirectoryExecutionException("unknown Back endpoint: " + from + " -> " + target);
        List<BackPlan> matches = new ArrayList<>();
        search(graph, from, target, new ArrayList<>(List.of(from)), new ArrayList<>(), matches);
        if (matches.isEmpty()) throw new DirectoryExecutionException("no adjacent Back path: " + from + " -> " + target);
        if (matches.size() != 1) throw new DirectoryExecutionException("ambiguous Back path: " + from + " -> " + target);
        return matches.get(0);
    }
    private static void search(DirectoryGraph graph, String current, String target, List<String> path,
                               List<BackEdge> actions, List<BackPlan> found) {
        if (found.size() > 1) return;
        if (current.equals(target)) { found.add(new BackPlan(path.get(0), target, path, actions)); return; }
        for (CaseEdge edge : graph.caseEdges()) {
            if (edge.target().equals(current) && !path.contains(edge.parent())) {
                path.add(edge.parent()); search(graph, edge.parent(), target, path, actions, found); path.remove(path.size() - 1);
            }
        }
        for (BackEdge edge : graph.backEdges()) {
            if (edge.from().equals(current) && !path.contains(edge.to())) {
                path.add(edge.to()); actions.add(edge);
                search(graph, edge.to(), target, path, actions, found);
                actions.remove(actions.size() - 1); path.remove(path.size() - 1);
            }
        }
    }
}
