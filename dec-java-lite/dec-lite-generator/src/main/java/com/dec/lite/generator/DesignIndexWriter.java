package com.dec.lite.generator;

import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.dec.lite.generator.JavaModelSupport.*;
import static com.dec.lite.generator.GenerationContextBuilder.topLevelEntries;

/** Writes Design ID to code traceability independently of source rendering. */
final class DesignIndexWriter {
    static void writeDesignIndex(Path target, DecProject project, GenerationContext context,
                                         List<String> generated) throws IOException {
        Map<String, List<String>> code = new LinkedHashMap<>();
        for (DesignProjectionGenerator.Source source : context.projections()) {
            for (String id : source.designIds()) addDesignCode(code, id, source.path());
        }
        for (Map<String, Object> model : context.entities()) {
            String id = String.valueOf(model.get("designId"));
            String name = String.valueOf(model.get("className"));
            String pkg = String.valueOf(model.get("package"));
            for (String file : List.of(javaPath(pkg, name + ".java"),
                    javaPath(siblingPackage(pkg, "dao"), name + "Dao.java"),
                    javaPath(siblingPackage(pkg, "mapper"), name + "Mapper.java"))) addDesignCode(code, id, file);
        }
        for (Map<String, Object> model : context.enums())
            addDesignCode(code, String.valueOf(model.get("designId")), javaPath(String.valueOf(model.get("package")), model.get("className") + ".java"));
        for (Map<String, Object> model : context.views()) {
            String id = String.valueOf(model.get("designId"));
            if (id.startsWith("VIEW-")) addDesignCode(code, id,
                    javaPath(String.valueOf(model.get("package")), model.get("className") + ".java"));
        }
        for (Map<String, Object> model : context.apis()) {
            String id = String.valueOf(model.get("designId"));
            String name = String.valueOf(model.get("groupClassName"));
            addDesignCode(code, id, javaPath(String.valueOf(model.get("package")), name + "Controller.java"));
            addDesignCode(code, id, javaPath(String.valueOf(model.get("servicePackage")), name + "Service.java"));
            for (Map<String, Object> param : topLevelEntries(project, DecKind.API, "apis")) {
                if (!id.equals(param.get("id"))) continue;
                Object request = param.get("request");
                if (!(request instanceof Map<?, ?> requestMap)) continue;
                Object rawParams = requestMap.get("params");
                if (!(rawParams instanceof Collection<?> params)) continue;
                for (Object raw : params) {
                    Map<String, Object> field = castMap(raw);
                    if (!(field.get("id") instanceof String fieldId)) continue;
                    String file = "body".equals(field.get("in"))
                            ? javaPath(String.valueOf(model.get("requestPackage")), model.get("requestClass") + ".java")
                            : javaPath(String.valueOf(model.get("package")), name + "Controller.java");
                    addDesignCode(code, fieldId, file);
                }
            }
        }
        for (Map<String, Object> model : context.enums()) {
            String file = javaPath(String.valueOf(model.get("package")), model.get("className") + ".java");
            for (Object value : (Collection<?>) model.get("values")) {
                Map<String, Object> item = castMap(value);
                addDesignCode(code, String.valueOf(item.get("designId")), file);
            }
        }
        Map<String, String> sources = new java.util.TreeMap<>();
        Map<String, String> kinds = new LinkedHashMap<>();
        Map<String, String> paths = new LinkedHashMap<>();
        for (DecDocument document : project.getDocuments()) {
            collectDesignIds(document.root(), document.source().getFileName().toString(),
                    document.kind().name().toLowerCase(Locale.ROOT), "", sources, kinds, paths);
        }
        StringBuilder markdown = new StringBuilder("# DEC Design ID → Code Index\n\n");
        markdown.append("YAML is the authoritative source; XML is the derived view. Search the listed source, path and Design ID to compare the design with generated Java. ")
                .append("System Information and Business flow classes are read-only design projections used as named references by application code. They preserve recognition, materialization, ordered actions, Produce and Back declarations; execution remains in the DEC runtime.\n\n")
                .append("| Design ID | YAML source | XML source | Kind | Generated Java |\n| --- | --- | --- | --- | --- |\n");
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            List<String> files = code.get(entry.getKey());
            String links = files == null || files.isEmpty() ? "DEC Runtime / application binding" : files.stream()
                    .map(file -> "[" + Path.of(file).getFileName() + "](" + file + ")")
                    .collect(Collectors.joining(", "));
            String path = paths.getOrDefault(entry.getKey(), "");
            String xml = entry.getValue().replaceFirst("\\.ya?ml$", ".xml");
            markdown.append("| `").append(entry.getKey()).append("` | `").append(entry.getValue()).append("#").append(path)
                    .append("` | `").append(xml).append("#").append(path).append("` | ")
                    .append(kinds.get(entry.getKey())).append(" | ").append(links).append(" |\n");
        }
        String relative = "DEC-DESIGN-INDEX.md";
        Files.writeString(target.resolve(relative), markdown.toString(), StandardCharsets.UTF_8);
        generated.add(relative);
    }

    static void addDesignCode(Map<String, List<String>> locations, String id, String file) {
        locations.computeIfAbsent(id, ignored -> new ArrayList<>()).add(file);
    }

    static void collectDesignIds(Object node, String source, String kind, String path,
                                         Map<String, String> sources, Map<String, String> kinds,
                                         Map<String, String> paths) {
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = castMap(raw);
            String nodePath = path;
            if (map.get("id") instanceof String id) {
                sources.put(id, source);
                kinds.put(id, kind);
                nodePath = path + "[id=" + id + "]";
                paths.put(id, nodePath);
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                collectDesignIds(entry.getValue(), source, kind,
                        nodePath.isEmpty() ? childPath : nodePath + "." + entry.getKey(), sources, kinds, paths);
            }
        } else if (node instanceof Collection<?> values) {
            for (Object value : values) collectDesignIds(value, source, kind, path, sources, kinds, paths);
        }
    }

}
