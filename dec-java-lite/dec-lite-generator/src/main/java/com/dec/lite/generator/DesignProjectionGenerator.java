package com.dec.lite.generator;

import com.dec.lite.information.InformationExpressionCompiler;
import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Readable, deterministic projections; never a second implementation of the runtime rules. */
final class DesignProjectionGenerator {
    record Source(String template, String path, Map<String, Object> model, List<String> designIds) { }

    List<Source> sources(DecProject project, String basePackage) {
        List<Source> sources = new ArrayList<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() == DecKind.SYSTEMS) {
                for (Object raw : list(document.root().get("systems"))) {
                    Map<String, Object> system = map(raw);
                    String name = text(system, "name");
                    Map<String, Object> model = common(basePackage, document, system);
                    model.put("package", basePackage + "." + segment(name) + ".information");
                    model.put("className", className(name) + "Information");
                    List<Map<String, Object>> nodes = new ArrayList<>();
                    for (Object value : list(system.get("information"))) {
                        Map<String, Object> info = map(value);
                        Map<String, Object> node = node(document, info);
                        node.put("key", literal(name + "." + text(info, "name")));
                        for (String field : List.of("viewRef", "ruleRef", "ruleData", "expression", "changeData"))
                            node.put(field, literal(info.get(field)));
                        String expression = (String) info.get("expression");
                        // Reuse the language compiler, including hyphenated keys and NOT/AND/OR grammar.
                        node.put("dependencies", expression == null ? List.of() :
                                new InformationExpressionCompiler().compile(expression).dependencies().stream()
                                        .sorted().map(key -> literal(key.toString())).toList());
                        nodes.add(node);
                    }
                    nodes.sort(Comparator.comparing(value -> String.valueOf(value.get("symbol"))));
                    uniqueSymbols(nodes);
                    model.put("nodes", nodes);
                    sources.add(source("information.java.ftl", model, ids(system)));
                }
            } else if (document.kind() == DecKind.BUSINESS) {
                Map<String, Object> business = map(document.root().get("business"));
                Map<String, Object> model = common(basePackage, document, business);
                model.put("package", basePackage + ".business");
                model.put("className", className(text(business, "name")) + "Flow");
                List<Map<String, Object>> nodes = new ArrayList<>();
                for (Object raw : list(business.get("directories"))) {
                    Map<String, Object> directory = map(raw);
                    Map<String, Object> node = node(document, directory);
                    node.put("type", literal(directory.getOrDefault("type", "normal")));
                    node.put("information", literal(text(directory, "informationRef")));
                    node.put("model", literal(text(directory, "modelRef")));
                    node.put("root", Boolean.TRUE.equals(directory.get("isRoot")));
                    node.put("dependencies", list(directory.get("dependencies")).stream()
                            .map(value -> literal(text(map(value), "informationRef"))).toList());
                    node.put("actions", actions(document, directory.get("actions")));
                    node.put("change", directory.get("change") == null ? "null" :
                            literal(text(map(directory.get("change")), "informationRef")));
                    List<Map<String, Object>> edges = new ArrayList<>();
                    for (Object value : list(directory.get("subDirectories"))) {
                        Map<String, Object> edge = map(value);
                        Map<String, Object> projection = new LinkedHashMap<>();
                        // rel is an execution predecessor by default; case edges go from result to case.
                        projection.put("rel", literal(text(edge, "rel")));
                        projection.put("role", literal(edge.getOrDefault("role", "execution")));
                        projection.put("information", literal(edge.get("informationRef")));
                        Map<String, Object> back = edge.get("back") == null ? Map.of() : map(edge.get("back"));
                        projection.put("back", literal(back.get("name")));
                        projection.put("backActions", actions(document, back.get("actions")));
                        edges.add(projection);
                    }
                    node.put("edges", edges);
                    nodes.add(node);
                }
                uniqueSymbols(nodes);
                model.put("nodes", nodes);
                sources.add(source("business-flow.java.ftl", model, ids(business)));
            }
        }
        if (!sources.isEmpty()) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("package", basePackage + ".design");
            model.put("className", "DecDesignMap");
            sources.add(source("design-map.java.ftl", model, List.of()));
        }
        Set<String> paths = new LinkedHashSet<>();
        for (Source source : sources) if (!paths.add(source.path()))
            throw new GenerationException("Design projection class collision: " + source.path());
        return List.copyOf(sources);
    }

    private static Map<String, Object> common(String base, DecDocument document, Map<String, Object> definition) {
        Map<String, Object> result = node(document, definition);
        result.put("basePackage", base);
        return result;
    }

    private static Map<String, Object> node(DecDocument document, Map<String, Object> definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        String id = text(definition, "id");
        result.put("id", literal(id));
        result.put("name", literal(text(definition, "name")));
        result.put("symbol", symbol(text(definition, "name")));
        var source = document.nodes().stream().filter(node -> id.equals(node.designId())).findFirst()
                .orElseThrow(() -> new GenerationException("Missing source location: " + id));
        result.put("source", literal(document.source().getFileName() + "#" + source.path()));
        return result;
    }

    private static List<Map<String, Object>> actions(DecDocument document, Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : list(raw)) {
            Map<String, Object> action = map(value);
            Map<String, Object> item = node(document, action);
            item.put("system", literal(action.get("systemRef")));
            item.put("rule", literal(action.get("ruleRef")));
            item.put("customType", literal(action.get("ruleRef") == null
                    ? action.getOrDefault("customType", action.get("name")) : null));
            List<Map<String, Object>> produces = new ArrayList<>();
            for (Object rawProduce : list(action.get("produces"))) {
                Map<String, Object> produce = map(rawProduce);
                produces.add(Map.of("id", literal(text(produce, "id")),
                        "ref", literal(text(produce, "ref")), "information", literal(produce.get("informationRef"))));
            }
            item.put("produces", produces);
            result.add(item);
        }
        return result; // Action order is semantic; never sort it.
    }

    private static Source source(String template, Map<String, Object> model, List<String> ids) {
        return new Source(template, "src/main/java/" + model.get("package").toString().replace('.', '/')
                + "/" + model.get("className") + ".java", model, ids);
    }

    private static List<String> ids(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            if (map.get("id") instanceof String id) result.add(id);
            map.values().forEach(child -> result.addAll(ids(child)));
        } else if (value instanceof Collection<?> values) values.forEach(child -> result.addAll(ids(child)));
        return List.copyOf(result);
    }

    private static void uniqueSymbols(List<Map<String, Object>> nodes) {
        Set<Object> symbols = new LinkedHashSet<>();
        for (var node : nodes) if (!symbols.add(node.get("symbol")))
            throw new GenerationException("Design projection constant collision: " + node.get("symbol"));
    }

    private static String symbol(String name) {
        String symbol = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
        if (symbol.isEmpty() || Character.isDigit(symbol.charAt(0))) symbol = "_" + symbol;
        if (Set.of("ALL", "ID", "NAME", "SOURCE").contains(symbol)) symbol = "NODE_" + symbol;
        return symbol;
    }

    private static String className(String name) {
        StringBuilder result = new StringBuilder();
        for (String part : name.split("[^A-Za-z0-9]+")) if (!part.isEmpty())
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        if (result.isEmpty() || Character.isDigit(result.charAt(0))) result.insert(0, '_');
        return result.toString();
    }

    private static String segment(String name) {
        String result = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_$]", "_");
        if (!result.matches("[a-z_$][a-z0-9_$]*") || javax.lang.model.SourceVersion.isKeyword(result))
            throw new GenerationException("Invalid System package: " + name);
        return result;
    }

    private static String literal(Object value) {
        if (value == null) return "null";
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (c < 32) result.append(String.format("\\%03o", (int) c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new GenerationException("Expected design mapping");
        return (Map<String, Object>) value;
    }
    private static List<?> list(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values)) throw new GenerationException("Expected design list");
        return values;
    }
    private static String text(Map<String, Object> value, String key) {
        if (!(value.get(key) instanceof String text) || text.isBlank())
            throw new GenerationException("Design projection requires " + key);
        return text;
    }
}
