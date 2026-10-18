package com.dec.lite.query;

import com.dec.lite.directory.DirectoryDefinition;
import com.dec.lite.directory.DirectoryGraph;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.util.*;

/** Resolves names once and selects graph ranges without executing Actions or inferring past events. */
final class BusinessQueryPlanner {
    record Selection(DirectoryQuery options, DirectoryDefinition anchor, List<String> nodes, List<String> trace) { }
    private final DirectoryGraph graph;
    private final Map<String, String> views;
    private final Map<String, String> directories;
    private final Map<String, Map<String, String>> relations;
    private final Map<String, List<String>> forward = new LinkedHashMap<>();
    private final Map<String, List<String>> reverse = new LinkedHashMap<>();

    BusinessQueryPlanner(DecProject project, DirectoryGraph graph, QuerySchema schema, BusinessQueryNames aliases) {
        this.graph = graph;
        Map<String, String> viewNames = new LinkedHashMap<>(), directoryNames = new LinkedHashMap<>();
        for (var document : project.getDocuments()) if (document.kind() == DecKind.VIEW)
            for (Object raw : QuerySchema.list(document.root().get("views"), "views")) {
                var view = QuerySchema.map(raw, "view"); String name = QuerySchema.text(view, "name");
                index(viewNames, name, name);
                String id = QuerySchema.optional(view, "id"); if (id != null) index(viewNames, id, name);
            }
        graph.directories().values().forEach(directory -> {
            index(directoryNames, directory.name(), directory.name());
            index(directoryNames, directory.id(), directory.name());
        });
        views = aliases(viewNames, aliases.views()); directories = aliases(directoryNames, aliases.directories());
        Map<String, Map<String, String>> relationNames = new LinkedHashMap<>();
        aliases.relations().forEach((view, values) -> {
            String name = resolve(views, view, "View");
            Map<String, String> paths = new LinkedHashMap<>();
            relationPaths(schema.view(name).properties(), "", paths);
            Map<String, String> resolved = aliases(paths, values);
            // Validate routing and relation targets even when an alias is not used by this query.
            values.values().forEach(path -> new ViewJoinPlanner(schema, schema.view(name)).include(path));
            if (relationNames.putIfAbsent(name, resolved) != null)
                throw new QueryCompilationException("duplicate View relation aliases: " + name);
        });
        relations = Map.copyOf(relationNames);
        graph.executionEdges().forEach(edge -> edge(edge.child(), edge.parent()));
        graph.caseEdges().forEach(edge -> edge(edge.parent(), edge.target()));
    }

    Selection select(BusinessQuery query) {
        String view = resolve(views, query.view(), "View");
        Set<String> selected;
        String start = query.start() == null ? null : directory(query.start(), view);
        String end = query.end() == null ? null : directory(query.end(), view);
        if (query.stage() != null) {
            String stage = directory(query.stage(), view);
            selected = new LinkedHashSet<>(List.of(stage));
            expandResult(selected, stage);
        } else {
            selected = start == null ? new LinkedHashSet<>(graph.directories().keySet()) : reachable(start, forward);
            selected.removeIf(name -> !view.equals(graph.directories().get(name).modelRef()));
            if (end != null) {
                if (!selected.contains(end)) throw new QueryCompilationException("no forward business range: " + start + " -> " + end);
                selected.retainAll(reachable(end, reverse));
                expandResult(selected, end);
            }
        }
        if (selected.isEmpty()) throw new QueryCompilationException("View has no Directory in this Business: " + view);
        for (String name : selected) if (!view.equals(graph.directories().get(name).modelRef()))
            throw new QueryCompilationException("business range crosses View: " + name);
        // A result is a classification container; only its selected case targets contribute predicates.
        selected.removeIf(name -> graph.directories().get(name).result());
        if (selected.isEmpty()) throw new QueryCompilationException("business query selects no stages");
        List<String> nodes = selected.stream().sorted().toList();
        DirectoryDefinition anchor = graph.directories().get(nodes.get(0));
        var old = query.options();
        List<String> with = old.with().stream().map(path -> relations.getOrDefault(view, Map.of()).getOrDefault(path, path)).toList();
        DirectoryQuery options = new DirectoryQuery(anchor.name(), null, null, null, with, old.filters(), old.offset(), old.size(), old.candidateLimit());
        return new Selection(options, anchor, nodes, List.of("queryMode=CURRENT_BUSINESS_STAGES", "view=" + view,
                "selector=eq:" + query.stage() + " start:" + start + " end:" + end, "selectedDirectories=" + nodes));
    }
    private String directory(String token, String view) {
        String name = resolve(directories, token, "Directory");
        if (!graph.directories().get(name).modelRef().equals(view)) throw new QueryCompilationException("Directory does not use View " + view + ": " + token);
        return name;
    }
    private void expandResult(Set<String> selected, String name) {
        if (graph.directories().get(name).result()) graph.caseEdges().stream()
                .filter(edge -> edge.parent().equals(name)).forEach(edge -> selected.add(edge.target()));
    }
    private void edge(String from, String to) {
        forward.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
        reverse.computeIfAbsent(to, ignored -> new ArrayList<>()).add(from);
    }
    private static Set<String> reachable(String start, Map<String, List<String>> edges) {
        Set<String> seen = new LinkedHashSet<>(); Deque<String> queue = new ArrayDeque<>(); queue.add(start);
        while (!queue.isEmpty()) { String name = queue.remove(); if (seen.add(name)) queue.addAll(edges.getOrDefault(name, List.of())); }
        return seen;
    }
    private static void relationPaths(Map<String, QuerySchema.Property> properties, String prefix, Map<String, String> paths) {
        properties.values().stream().filter(QuerySchema.Property::isRelation).forEach(property -> {
            String path = prefix + property.name(); index(paths, path, path); relationPaths(property.children(), path + ".", paths);
        });
    }
    private static Map<String, String> aliases(Map<String, String> canonical, Map<String, String> aliases) {
        Map<String, String> result = new LinkedHashMap<>(canonical);
        aliases.forEach((alias, target) -> index(result, alias, resolve(canonical, target, "alias target")));
        return Map.copyOf(result);
    }
    private static void index(Map<String, String> index, String token, String target) {
        if (token == null || token.isBlank()) throw new QueryCompilationException("blank query name");
        String old = index.putIfAbsent(token, target);
        if (old != null && !old.equals(target)) throw new QueryCompilationException("ambiguous query name: " + token);
    }
    private static String resolve(Map<String, String> index, String token, String kind) {
        String value = index.get(token);
        if (value == null) throw new QueryCompilationException("unknown " + kind + ": " + token);
        return value;
    }
}
