package com.dec.lite.information;

import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Parses System-owned Information and publishes a validated immutable DAG. */
public final class InformationParser {
    public InformationCompilation parse(DecProject project) {
        if (project == null) throw new IllegalArgumentException("project is required");
        Map<InformationKey, InformationDefinition> definitions = new TreeMap<>();
        ModelExpressionCompiler modelCompiler = new ModelExpressionCompiler();
        InformationExpressionCompiler informationCompiler = new InformationExpressionCompiler();
        Map<InformationKey, ModelExpressionCompiler.CompiledExpression> modelExpressions = new HashMap<>();
        Map<InformationKey, InformationExpression> informationExpressions = new HashMap<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != DecKind.SYSTEMS) continue;
            Object systemsRaw = document.root().get("systems");
            if (!(systemsRaw instanceof Collection<?> systems)) throw new InformationCompilationException("systems document requires list 'systems'");
            for (Object rawSystem : systems) {
                Map<String, Object> system = map(rawSystem, "system");
                String systemName = text(system, "name", "system");
                Object rawInformation = system.get("information");
                if (rawInformation == null) continue;
                if (!(rawInformation instanceof Collection<?> information)) throw new InformationCompilationException("system " + systemName + " information must be a list");
                for (Object rawDefinition : information) {
                    Map<String, Object> value = map(rawDefinition, "information");
                    InformationDefinition definition = definition(systemName, value, document.source());
                    if (definitions.putIfAbsent(definition.key(), definition) != null) throw new InformationCompilationException("duplicate Information key: " + definition.key());
                    if (definition.ruleData() != null) modelExpressions.put(definition.key(), modelCompiler.compile(definition.ruleData()));
                    if (definition.expression() != null) informationExpressions.put(definition.key(), informationCompiler.compile(definition.expression()));
                }
            }
        }
        validateReferences(definitions, informationExpressions);
        Map<InformationKey, Set<InformationKey>> dependencies = new TreeMap<>();
        Map<InformationKey, Set<InformationKey>> reverse = new TreeMap<>();
        for (InformationDefinition definition : definitions.values()) {
            Set<InformationKey> values = new LinkedHashSet<>();
            InformationExpression expression = informationExpressions.get(definition.key());
            if (expression != null) values.addAll(expression.dependencies());
            dependencies.put(definition.key(), Set.copyOf(values));
            for (InformationKey dependency : values) reverse.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(definition.key());
        }
        List<InformationKey> topological = topological(definitions.keySet(), dependencies);
        return new InformationCompilation(definitions, modelExpressions, informationExpressions, dependencies, reverse, topological, digest(definitions, dependencies));
    }

    private static InformationDefinition definition(String system, Map<String, Object> value, Path source) {
        String id = text(value, "id", "information");
        String name = text(value, "name", "information");
        String viewRef = optionalText(value.get("viewRef"));
        String ruleRef = optionalText(value.get("ruleRef"));
        String ruleData = optionalText(value.get("ruleData"));
        String changeData = optionalText(value.get("changeData"));
        String expression = optionalText(value.get("expression"));
        int recognizers = (ruleRef != null ? 1 : 0) + (ruleData != null ? 1 : 0) + (expression != null ? 1 : 0);
        if (recognizers != 1) throw new InformationCompilationException("Information " + system + "." + name + " must define exactly one of ruleRef, ruleData, expression");
        if (expression != null && (viewRef != null || changeData != null)) throw new InformationCompilationException("composite Information cannot define viewRef/changeData: " + system + "." + name);
        if (ruleRef != null && viewRef == null) throw new InformationCompilationException("ruleRef Information requires viewRef: " + system + "." + name);
        if (ruleData != null && viewRef == null) throw new InformationCompilationException("ruleData Information requires viewRef: " + system + "." + name);
        if (changeData != null && ruleData == null) throw new InformationCompilationException("changeData requires ruleData: " + system + "." + name);
        return new InformationDefinition(id, new InformationKey(system, name), viewRef, ruleRef, ruleData, changeData, expression, source);
    }

    private static void validateReferences(Map<InformationKey, InformationDefinition> definitions, Map<InformationKey, InformationExpression> expressions) {
        for (Map.Entry<InformationKey, InformationExpression> entry : expressions.entrySet()) for (InformationKey dependency : entry.getValue().dependencies()) if (!definitions.containsKey(dependency)) throw new InformationCompilationException("unknown Information reference " + dependency + " from " + entry.getKey());
    }

    private static List<InformationKey> topological(Set<InformationKey> keys, Map<InformationKey, Set<InformationKey>> dependencies) {
        Map<InformationKey, Integer> state = new HashMap<>(); List<InformationKey> result = new ArrayList<>();
        for (InformationKey key : keys) visit(key, dependencies, state, result);
        return List.copyOf(result);
    }
    private static void visit(InformationKey key, Map<InformationKey, Set<InformationKey>> dependencies, Map<InformationKey, Integer> state, List<InformationKey> result) {
        int current = state.getOrDefault(key, 0); if (current == 1) throw new InformationCompilationException("Information dependency cycle at " + key); if (current == 2) return; state.put(key, 1); for (InformationKey dependency : dependencies.getOrDefault(key, Set.of())) visit(dependency, dependencies, state, result); state.put(key, 2); result.add(key);
    }

    private static String digest(Map<InformationKey, InformationDefinition> definitions, Map<InformationKey, Set<InformationKey>> dependencies) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (InformationKey key : definitions.keySet().stream().sorted(java.util.Comparator.comparing(InformationKey::toString)).toList()) {
                InformationDefinition definition = definitions.get(key);
                String line = key + "|" + definition.designId() + "|" + definition.kind() + "|" + definition.viewRef()
                        + "|" + definition.ruleRef() + "|" + normalized(definition.ruleData()) + "|"
                        + normalized(definition.changeData()) + "|" + normalized(definition.expression()) + "|"
                        + dependencies.getOrDefault(key, Set.of()).stream().map(Object::toString).sorted().toList() + "\n";
                digest.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format("%02x", value)); return result.toString();
        } catch (Exception exception) { throw new InformationCompilationException("cannot calculate Information graph digest", exception); }
    }
    private static String normalized(String value) { return value == null ? "" : value.stripTrailing(); }

    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value, String context) { if (!(value instanceof Map<?, ?> raw)) throw new InformationCompilationException(context + " must be a mapping"); Map<String, Object> result = new LinkedHashMap<>(); for (Map.Entry<?, ?> entry : raw.entrySet()) { if (!(entry.getKey() instanceof String key)) throw new InformationCompilationException(context + " keys must be strings"); result.put(key, entry.getValue()); } return result; }
    private static String text(Map<String, Object> value, String key, String context) { String text = optionalText(value.get(key)); if (text == null) throw new InformationCompilationException(context + " requires " + key); return text; }
    private static String optionalText(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
}
