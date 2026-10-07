package com.dec.lite.parser;

import com.dec.lite.model.DecDesignNode;
import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;
import com.dec.lite.model.DesignGap;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Loads canonical DEC YAML documents from a file or recursively from a directory. */
public final class DecYamlParser {
    private static final Pattern DESIGN_ID = Pattern.compile("^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+$");
    private static final Map<DecKind, String> PRIMARY_COLLECTIONS = Map.of(
            DecKind.DATA, "datas", DecKind.VIEW, "views", DecKind.RULE, "ruleViews",
            DecKind.API, "apis", DecKind.ENUM, "enums", DecKind.SYSTEMS, "systems");

    private final Yaml yaml;

    public DecYamlParser() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(32);
        options.setNestingDepthLimit(64);
        options.setCodePointLimit(8_000_000);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    /** Parse a canonical DEC YAML file or directory; legacy and derived inputs fail closed. */
    public DecProject parse(Path yamlPath) {
        if (yamlPath == null) throw new IllegalArgumentException("yaml path is required");
        Path input = yamlPath.toAbsolutePath().normalize();
        if (!Files.exists(input)) throw new DecParseException(input, "input does not exist");
        try {
            List<Path> files = collectYamlFiles(input);
            if (files.isEmpty()) throw new DecParseException(input, "no .yaml or .yml files found");
            DecProject project = new DecProject();
            for (Path file : files) parseFile(file, project);
            return project.freeze();
        } catch (DecParseException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new DecParseException(input, e.getMessage(), e);
        } catch (Exception e) {
            throw new DecParseException(input, "cannot read YAML input", e);
        }
    }

    private void parseFile(Path file, DecProject project) {
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(file)) {
            if (Files.size(file) > 8_000_000) throw new DecParseException(file, "YAML document exceeds 8 MB limit");
            List<Object> documents = new ArrayList<>();
            yaml.loadAll(reader).forEach(documents::add);
            if (documents.size() != 1 || !(documents.get(0) instanceof Map<?, ?> raw)) {
                throw new DecParseException(file, "expected exactly one YAML mapping document");
            }
            root = stringKeyMap(raw, file);
        } catch (DecParseException e) {
            throw e;
        } catch (Exception e) {
            String location = e instanceof org.yaml.snakeyaml.error.MarkedYAMLException marked && marked.getProblemMark() != null
                    ? " at line " + (marked.getProblemMark().getLine() + 1) + ", column " + (marked.getProblemMark().getColumn() + 1)
                    : "";
            throw new DecParseException(file, "cannot parse YAML" + location, e);
        }
        addRoot(root, file, project);
    }

    /** Shared canonical AST ingestion for YAML and XML frontends. */
    static void addRoot(Map<String, Object> root, Path file, DecProject project) {
        rejectDerivedArtifacts(root, file);
        DecKind kind;
        try {
            kind = DecKind.parse(root.get("kind"));
        } catch (IllegalArgumentException e) {
            throw new DecParseException(file, e.getMessage());
        }
        if (!(root.get("version") instanceof String version) || version.isBlank()) {
            throw new DecParseException(file, "canonical DEC document requires non-empty version");
        }
        List<DecDesignNode> nodes = new ArrayList<>();
        collectNodes(kind, root, file, nodes, project);
        project.addDocument(new DecDocument(kind, version, file, root, nodes));
    }

    private static void rejectDerivedArtifacts(Map<String, Object> root, Path file) {
        Object artifact = root.get("artifact");
        if (artifact instanceof Map<?, ?> map && "html-recovery".equals(map.get("producer"))) {
            throw new DecParseException(file, "HTML recovery artifact is not canonical DEC YAML");
        }
        for (String key : List.of("sourceText", "source_rows", "informationTree")) {
            if (root.containsKey(key)) {
                throw new DecParseException(file, "derived HTML field '" + key + "' is not canonical DEC YAML");
            }
        }
    }

    private static void collectNodes(DecKind kind, Map<String, Object> root, Path source,
                                     List<DecDesignNode> nodes, DecProject project) {
        String collection = PRIMARY_COLLECTIONS.get(kind);
        if (collection != null) {
            Object rawEntries = root.get(collection);
            if (!(rawEntries instanceof List<?> entries)) {
                project.addGap(new DesignGap("MISSING_DESIGN_COLLECTION", kind, null, source,
                        "canonical document requires list field '" + collection + "'"));
            } else {
                for (int i = 0; i < entries.size(); i++) {
                    if (!(entries.get(i) instanceof Map<?, ?> raw)) {
                        project.addGap(new DesignGap("INVALID_DESIGN_NODE", kind, null, source,
                                collection + "[" + i + "] must be a mapping"));
                        continue;
                    }
                    Map<String, Object> entry = stringKeyMap(raw, source);
                    String id = entry.get("id") instanceof String text ? text : null;
                    if (id == null || id.isBlank()) {
                        project.addGap(new DesignGap("MISSING_DESIGN_ID", kind, null, source,
                                collection + "[" + i + "] requires stable field 'id'"));
                    } else if (!DESIGN_ID.matcher(id).matches()) {
                        project.addGap(new DesignGap("INVALID_DESIGN_ID", kind, id, source,
                                collection + "[" + i + "].id has invalid stable Design ID format"));
                    }
                }
            }
        } else if (kind == DecKind.BUSINESS && !(root.get("business") instanceof Map<?, ?>)) {
            project.addGap(new DesignGap("MISSING_DESIGN_NODE", kind, null, source,
                    "canonical business document requires mapping field 'business'"));
        }
        collectExplicitIds(kind, root, "root", source, nodes, new IdentityHashMap<>());
        requireNestedIds(kind, root, source, project);
    }

    private static void requireNestedIds(DecKind kind, Map<String, Object> root, Path source, DecProject project) {
        switch (kind) {
            case BUSINESS -> {
                if (!(root.get("business") instanceof Map<?, ?> business)) return;
                requireId(kind, business, "business", source, project);
                for (Object rawDirectory : values(business.get("directories"))) {
                    if (!(rawDirectory instanceof Map<?, ?> directory)) continue;
                    requireId(kind, directory, "business.directories[]", source, project);
                    requireActionIds(kind, directory.get("actions"), "business.directories[].actions[]", source, project);
                    for (Object rawEdge : values(directory.get("subDirectories"))) {
                        if (rawEdge instanceof Map<?, ?> edge && edge.get("back") instanceof Map<?, ?> back)
                            requireActionIds(kind, back.get("actions"), "business.directories[].subDirectories[].back.actions[]", source, project);
                    }
                }
            }
            case SYSTEMS -> {
                for (Object rawSystem : values(root.get("systems"))) if (rawSystem instanceof Map<?, ?> system)
                    for (Object rawInfo : values(system.get("information")))
                        if (rawInfo instanceof Map<?, ?> info) requireId(kind, info, "systems[].information[]", source, project);
            }
            case RULE -> {
                for (Object rawView : values(root.get("ruleViews"))) if (rawView instanceof Map<?, ?> view)
                    for (Object rawRule : values(view.get("rules")))
                        if (rawRule instanceof Map<?, ?> rule) requireId(kind, rule, "ruleViews[].rules[]", source, project);
            }
            case API -> {
                for (Object rawApi : values(root.get("apis"))) if (rawApi instanceof Map<?, ?> api && api.get("request") instanceof Map<?, ?> request)
                    for (Object rawParam : values(request.get("params")))
                        if (rawParam instanceof Map<?, ?> param) requireId(kind, param, "apis[].request.params[]", source, project);
            }
            case ENUM -> {
                for (Object rawEnum : values(root.get("enums"))) if (rawEnum instanceof Map<?, ?> enumeration)
                    for (Object rawValue : values(enumeration.get("values")))
                        if (rawValue instanceof Map<?, ?> value) requireId(kind, value, "enums[].values[]", source, project);
            }
            default -> { }
        }
    }
    private static void requireActionIds(DecKind kind, Object rawActions, String path, Path source, DecProject project) {
        for (Object rawAction : values(rawActions)) if (rawAction instanceof Map<?, ?> action) {
            requireId(kind, action, path, source, project);
            for (Object rawProduce : values(action.get("produces")))
                if (rawProduce instanceof Map<?, ?> produce) requireId(kind, produce, path + ".produces[]", source, project);
        }
    }
    private static List<?> values(Object raw) { return raw instanceof List<?> list ? list : List.of(); }
    private static void requireId(DecKind kind, Map<?, ?> node, String path, Path source, DecProject project) {
        Object raw = node.get("id");
        if (!(raw instanceof String id) || id.isBlank())
            project.addGap(new DesignGap("MISSING_DESIGN_ID", kind, null, source, path + " requires stable field 'id'"));
        else if (!DESIGN_ID.matcher(id).matches())
            project.addGap(new DesignGap("INVALID_DESIGN_ID", kind, id, source, path + ".id has invalid stable Design ID format"));
    }

    private static void collectExplicitIds(DecKind kind, Object value, String path, Path source,
                                           List<DecDesignNode> nodes, IdentityHashMap<Object, Boolean> seen) {
        if (value instanceof Map<?, ?> raw) {
            if (seen.put(value, Boolean.TRUE) != null) return;
            Map<String, Object> map = stringKeyMap(raw, source);
            Object rawId = map.get("id");
            if (rawId instanceof String id && DESIGN_ID.matcher(id).matches()) {
                String name = map.get("name") instanceof String text ? text : null;
                nodes.add(new DecDesignNode(kind, id, name, path, source, map));
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                collectExplicitIds(kind, entry.getValue(), path + "." + entry.getKey(), source, nodes, seen);
            }
        } else if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                collectExplicitIds(kind, item, path + "[" + index++ + "]", source, nodes, seen);
            }
        }
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> raw, Path source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new DecParseException(source, "YAML mapping keys must be strings");
            }
            result.put(key, normalize(entry.getValue(), source));
        }
        return result;
    }

    private static Object normalize(Object value, Path source) {
        if (value instanceof Map<?, ?> map) return stringKeyMap(map, source);
        if (value instanceof List<?> list) return list.stream().map(item -> normalize(item, source)).collect(Collectors.toList());
        return value;
    }

    private static List<Path> collectYamlFiles(Path input) throws java.io.IOException {
        if (Files.isRegularFile(input)) return List.of(input);
        try (var stream = Files.walk(input)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yaml") || path.getFileName().toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(Path::toString)).collect(Collectors.toList());
        }
    }
}
