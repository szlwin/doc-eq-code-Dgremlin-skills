package com.dec.lite.directory;

import com.dec.lite.action.ActionDefinition;
import com.dec.lite.action.BusinessActionParser;
import com.dec.lite.action.RuleViewCompiler;
import com.dec.lite.action.CustomActionRegistry;
import com.dec.lite.information.RuleViewKey;
import com.dec.lite.information.InformationCompilation;
import com.dec.lite.information.InformationKey;
import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles canonical Business directories into three disjoint edge sets. */
public final class DirectoryGraphCompiler {
    /** Strict readiness gate for a concrete runtime instance. */
    public DirectoryGraph compileReady(DecProject project, InformationCompilation information,
                                       CustomActionRegistry customActions) {
        DirectoryGraph graph = compile(project, information);
        customActions.validate(new BusinessActionParser().parse(project));
        return graph;
    }

    public DirectoryGraph compile(DecProject project, InformationCompilation information) {
        Map<String, DirectoryDefinition> directories = new LinkedHashMap<>();
        List<ExecutionEdge> execution = new ArrayList<>();
        List<CaseEdge> cases = new ArrayList<>();
        List<BackEdge> back = new ArrayList<>();
        List<ActionDefinition> allActions = new BusinessActionParser().parse(project);
        Map<RuleViewKey, com.dec.lite.action.CompiledRuleView> ruleViews = new RuleViewCompiler().compile(project);
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != DecKind.BUSINESS) continue;
            Map<String, Object> business = map(document.root().get("business"), "business");
            for (Object raw : list(business.get("directories"), "business.directories")) {
                Map<String, Object> value = map(raw, "directory");
                String name = text(value, "name", "directory");
                if (value.containsKey("viewRef") || value.containsKey("view-ref"))
                    throw new DirectoryCompilationException("legacy Directory viewRef is unsupported: " + name);
                InformationKey target = key(value.get("informationRef"), "directory.informationRef");
                List<InformationKey> dependencies = new ArrayList<>();
                for (Object dependency : listOrEmpty(value.get("dependencies"))) dependencies.add(key(map(dependency, "dependency").get("informationRef"), "dependency.informationRef"));
                InformationKey change = value.get("change") instanceof Map<?, ?> rawChange
                        ? key(map(rawChange, "change").get("informationRef"), "change.informationRef") : null;
                List<ActionDefinition> actions = allActions.stream().filter(action -> name.equals(action.ownerDirectory())).toList();
                DirectoryDefinition definition = new DirectoryDefinition(text(value, "id", "directory"), name,
                        optional(value, "type"), text(value, "modelRef", "directory"), target,
                        Boolean.TRUE.equals(value.get("isRoot")), dependencies, actions, change, document.source());
                if (directories.putIfAbsent(name, definition) != null) throw new DirectoryCompilationException("duplicate Directory: " + name);
                for (Object rawEdge : listOrEmpty(value.get("subDirectories"))) {
                    Map<String, Object> edge = map(rawEdge, "subDirectory");
                    String rel = text(edge, "rel", "subDirectory");
                    String role = optional(edge, "role");
                    if (edge.containsKey("anyOne") || edge.containsKey("mutualExclusion") || edge.containsKey("any-one") || edge.containsKey("mutual-exclusion"))
                        throw new DirectoryCompilationException("legacy subDirectory any-one/mutual-exclusion is unsupported: " + name + " -> " + rel);
                    if ("predecessor".equals(role)) throw new DirectoryCompilationException("predecessor role is forbidden: " + name + " -> " + rel);
                    if (role == null) {
                        execution.add(new ExecutionEdge(rel, name));
                        if (edge.get("back") instanceof Map<?, ?>) {
                            List<ActionDefinition> backActions = allActions.stream().filter(action -> (name + ".back." + rel).equals(action.ownerDirectory())).toList();
                            back.add(new BackEdge(name, rel, backActions));
                        }
                    } else if ("case".equals(role)) {
                        cases.add(new CaseEdge(name, rel, key(edge.get("informationRef"), "case.informationRef")));
                    } else throw new DirectoryCompilationException("unsupported subDirectory role: " + role);
                }
            }
        }
        String root = null;
        for (DirectoryDefinition directory : directories.values()) {
            if (directory.root()) {
                if (root != null) throw new DirectoryCompilationException("multiple root Directories: " + root + ", " + directory.name());
                root = directory.name();
            }
        }
        if (root == null) throw new DirectoryCompilationException("Directory root is required");
        validate(directories, execution, cases, back, root, information, allActions, ruleViews);
        return new DirectoryGraph(directories, execution, cases, back, root);
    }

    private static void validate(Map<String, DirectoryDefinition> directories, List<ExecutionEdge> execution,
                                 List<CaseEdge> cases, List<BackEdge> back, String root, InformationCompilation information,
                                 List<ActionDefinition> actions, Map<RuleViewKey, com.dec.lite.action.CompiledRuleView> ruleViews) {
        Map<String, String> parentOf = new HashMap<>();
        for (ExecutionEdge edge : execution) {
            require(directories, edge.child()); require(directories, edge.parent());
            String prior = parentOf.putIfAbsent(edge.child(), edge.parent());
            if (prior != null) throw new DirectoryCompilationException("execution child has multiple parents: " + edge.child());
            if (edge.child().equals(edge.parent())) throw new DirectoryCompilationException("Directory self-cycle: " + edge.child());
        }
        for (CaseEdge edge : cases) {
            require(directories, edge.parent()); require(directories, edge.target());
            if (!directories.get(edge.parent()).result()) throw new DirectoryCompilationException("case owner is not a result Directory: " + edge.parent());
            if (!directories.get(edge.target()).dependencies().isEmpty()) throw new DirectoryCompilationException("case target cannot define dependencies: " + edge.target());
            if (parentOf.containsKey(edge.target())) throw new DirectoryCompilationException("case target is also an execution child: " + edge.target());
            if (edge.target().equals(root)) throw new DirectoryCompilationException("root cannot be a case target");
        }
        for (BackEdge edge : back) {
            if (execution.stream().noneMatch(forward -> forward.child().equals(edge.to()) && forward.parent().equals(edge.from()))) throw new DirectoryCompilationException("Back edge has no execution relationship: " + edge);
        }
        for (DirectoryDefinition directory : directories.values()) {
            ensureInformation(information, directory.informationRef());
            for (InformationKey dependency : directory.dependencies()) ensureInformation(information, dependency);
            if (directory.changeInformation() != null) ensureInformation(information, directory.changeInformation());
            if (directory.result() && cases.stream().noneMatch(edge -> edge.parent().equals(directory.name()))) throw new DirectoryCompilationException("result Directory has no cases: " + directory.name());
        }
        for (CaseEdge edge : cases) ensureInformation(information, edge.informationRef());
        for (ActionDefinition action : actions) {
            if (action.isRuleView()) {
                long matches = ruleViews.keySet().stream().filter(key -> key.system().equals(action.systemRef()) && key.ruleRef().equals(action.ruleRef())).count();
                if (matches != 1) throw new DirectoryCompilationException("Action RuleView binding must be unique: " + action.systemRef() + "." + action.ruleRef() + " matches=" + matches);
            }
            for (var produce : action.produces()) if (produce.informationRef() != null) ensureInformation(information, produce.informationRef());
        }
        Map<String, List<String>> next = new HashMap<>();
        for (ExecutionEdge edge : execution) next.computeIfAbsent(edge.child(), ignored -> new ArrayList<>()).add(edge.parent());
        for (CaseEdge edge : cases) next.computeIfAbsent(edge.parent(), ignored -> new ArrayList<>()).add(edge.target());
        Set<String> visited = new HashSet<>(); Set<String> stack = new HashSet<>();
        visit(root, next, visited, stack);
        if (visited.size() != directories.size()) throw new DirectoryCompilationException("unreachable Directories: " + difference(directories.keySet(), visited));
    }
    private static void visit(String node, Map<String, List<String>> next, Set<String> visited, Set<String> stack) {
        if (!stack.add(node)) throw new DirectoryCompilationException("Directory graph cycle at " + node);
        if (visited.add(node)) for (String child : next.getOrDefault(node, List.of())) visit(child, next, visited, stack);
        stack.remove(node);
    }
    private static Set<String> difference(Set<String> all, Set<String> seen) { Set<String> result = new LinkedHashSet<>(all); result.removeAll(seen); return result; }
    private static void ensureInformation(InformationCompilation information, InformationKey key) {
        if (!information.definitions().containsKey(key)) throw new DirectoryCompilationException("unknown Information: " + key);
    }
    private static void require(Map<String, DirectoryDefinition> definitions, String name) { if (!definitions.containsKey(name)) throw new DirectoryCompilationException("unknown Directory: " + name); }
    private static InformationKey key(Object raw, String context) { if (!(raw instanceof String text)) throw new DirectoryCompilationException(context + " is required"); return InformationKey.parse(text); }
    private static String text(Map<String, Object> map, String field, String context) { String value = optional(map, field); if (value == null) throw new DirectoryCompilationException(context + " requires " + field); return value; }
    private static String optional(Map<String, Object> map, String field) { return map.get(field) instanceof String value && !value.isBlank() ? value : null; }
    private static List<?> list(Object raw, String context) { if (!(raw instanceof List<?> list)) throw new DirectoryCompilationException(context + " must be a list"); return list; }
    private static List<?> listOrEmpty(Object raw) { return raw instanceof List<?> list ? list : List.of(); }
    private static Map<String, Object> map(Object raw, String context) {
        if (!(raw instanceof Map<?, ?> value)) throw new DirectoryCompilationException(context + " must be a mapping");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) { if (!(entry.getKey() instanceof String key)) throw new DirectoryCompilationException(context + " keys must be strings"); result.put(key, entry.getValue()); }
        return result;
    }
}
