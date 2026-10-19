package com.dec.lite.generator;

import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dec.lite.generator.JavaModelSupport.*;
import static com.dec.lite.generator.DataModelBuilder.*;
import static com.dec.lite.generator.ApiModelBuilder.*;

/** Resolves design ownership and assembles template models before any output is changed. */
final class GenerationContextBuilder {
    static GenerationContext buildContext(DecProject project, String basePackage, GenerationOptions options) {
        List<Map<String, Object>> dataDefinitions = topLevelEntries(project, DecKind.DATA, "datas");
        List<Map<String, Object>> enumDefinitions = topLevelEntries(project, DecKind.ENUM, "enums");
        List<Map<String, Object>> viewDefinitions = topLevelEntries(project, DecKind.VIEW, "views");
        List<Map<String, Object>> apiDefinitions = topLevelEntries(project, DecKind.API, "apis");
        options.validateApiIds(apiDefinitions.stream().map(api -> requiredText(api, "id", "api"))
                .collect(java.util.stream.Collectors.toSet()));
        Map<String, Map<String, Object>> dataByName = indexByName(dataDefinitions);
        Map<String, String> dataSources = dataSources(project);
        validateDataSources(dataDefinitions, dataSources);
        Map<String, Map<String, String>> dataEnumRefs = dataEnumRefs(dataDefinitions);
        Map<String, String> enumTypes = new HashMap<>();
        for (Map<String, Object> definition : enumDefinitions) {
            String name = requiredText(definition, "name", "enum");
            enumTypes.put(name, className(name));
        }
        Map<String, String> enumPackages = enumPackages(enumDefinitions, dataDefinitions, apiDefinitions,
                viewDefinitions, basePackage);
        validateDataDefinitions(dataDefinitions, dataSources, enumTypes);
        Map<String, String> viewTypes = new HashMap<>();
        Map<String, String> viewPackages = new HashMap<>();
        for (Map<String, Object> definition : viewDefinitions) {
            String name = requiredText(definition, "name", "view");
            viewTypes.put(name, className(name));
            viewPackages.put(name, systemPackage(definition, basePackage) + ".view");
        }

        List<Map<String, Object>> entities = new ArrayList<>();
        for (Map<String, Object> definition : dataDefinitions) entities.add(entityModel(definition, basePackage, enumTypes, enumPackages, dataEnumRefs));
        List<Map<String, Object>> enums = new ArrayList<>();
        for (Map<String, Object> definition : enumDefinitions) enums.add(enumModel(definition, enumPackages));
        List<Map<String, Object>> views = new ArrayList<>();
        for (Map<String, Object> definition : viewDefinitions) {
            views.add(viewModel(definition, basePackage, dataByName, enumTypes, enumPackages, viewTypes, viewPackages, dataEnumRefs, views));
        }
        List<Map<String, Object>> requests = new ArrayList<>();
        List<Map<String, Object>> apis = new ArrayList<>();
        for (Map<String, Object> definition : apiDefinitions) {
            validateApiContract(definition, enumTypes, viewTypes);
            Map<String, Object> api = apiModel(definition, basePackage, enumTypes, enumPackages, viewTypes, viewPackages, options);
            Object request = api.remove("requestModel");
            if (request instanceof Map<?, ?> requestMap) requests.add(castMap(requestMap));
            Object response = api.remove("responseModel");
            if (response instanceof Map<?, ?> responseMap) views.add(castMap(responseMap));
            apis.add(api);
        }
        apiGroups(apis);
        Map<String, Object> application = Map.of("package", basePackage, "className", "GeneratedApplication");
        List<Map<String, Object>> dataSourceModels = dataSources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> model = new LinkedHashMap<>();
                    model.put("name", entry.getKey());
                    model.put("type", entry.getValue());
                    return model;
                })
                .toList();
        return new GenerationContext(basePackage, projectModel(basePackage, dataSources.values().stream().anyMatch("MySQL"::equalsIgnoreCase)), application, new DesignProjectionGenerator().sources(project, basePackage),
                entities, enums, views, requests, apis, dataSourceModels);
    }

    private static Map<String, Object> projectModel(String basePackage, boolean mysql) {
        Map<String, Object> model = new LinkedHashMap<>(GenerationVersions.DEFAULTS);
        model.putAll(Map.of("groupId", basePackage, "artifactId", "generated-dec-project", "version", "0.1.0", "mysql", mysql));
        return Map.copyOf(model);
    }

    static List<Map<String, Object>> topLevelEntries(DecProject project, DecKind kind, String key) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != kind) continue;
            Object raw = document.root().get(key);
            if (raw instanceof Collection<?> collection) for (Object item : collection) {
                Map<String, Object> definition = new LinkedHashMap<>(castMap(item));
                String source = document.source().getFileName().toString();
                definition.put("designSource", source);
                definition.put("designXml", source.replaceFirst("\\.ya?ml$", ".xml"));
                definition.put("designPath", key + "[id=" + requiredText(definition, "id", key) + "]");
                result.add(definition);
            }
        }
        result.sort(Comparator.comparing(item -> String.valueOf(item.get("id"))));
        return result;
    }

    private static Map<String, String> dataSources(DecProject project) {
        Map<String, String> result = new LinkedHashMap<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != DecKind.CONFIG) continue;
            collectDataSources(document.root().get("dataSources"), result);
            collectDataSources(document.root().get("datasources"), result);
            Object info = document.root().get("dataSourceInfo");
            if (info == null) info = document.root().get("datasourceInfo");
            if (info instanceof Map<?, ?> map) {
                Map<String, Object> infoMap = castMap(map);
                collectDataSources(infoMap.get("dataSources"), result);
                collectDataSources(infoMap.get("datasources"), result);
            }
        }
        return result;
    }

    private static void collectDataSources(Object raw, Map<String, String> result) {
        if (!(raw instanceof Collection<?> collection)) return;
        for (Object item : collection) {
            Map<String, Object> source = castMap(item);
            String name = requiredText(source, "name", "dataSource");
            String type = requiredText(source, "type", "dataSource");
            String previous = result.putIfAbsent(name, type);
            if (previous != null && !previous.equals(type)) {
                throw new GenerationException("DESIGN_GAP dataSource has conflicting types: " + name);
            }
        }
    }

    private static void validateDataSources(List<Map<String, Object>> definitions, Map<String, String> dataSources) {
        for (Map<String, Object> data : definitions) {
            Object tables = data.get("tables");
            if (!(tables instanceof Collection<?> collection)) continue;
            for (Object raw : collection) {
                Map<String, Object> table = castMap(raw);
                String name = table.get("dataSource") instanceof String value ? value : null;
                if (name == null || name.isBlank()) throw new GenerationException("DESIGN_GAP data table requires dataSource: " + data.get("name"));
                if (!dataSources.containsKey(name)) throw new GenerationException("DESIGN_GAP dataSource is not declared: " + name);
            }
        }
    }

    private static Map<String, Map<String, String>> dataEnumRefs(List<Map<String, Object>> definitions) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Map<String, Object> data : definitions) {
            String dataName = requiredText(data, "name", "data");
            Map<String, String> refs = new HashMap<>();
            Object tables = data.get("tables");
            if (tables instanceof Collection<?> collection) {
                for (Object rawTable : collection) {
                    Map<String, Object> table = castMap(rawTable);
                    Object columns = table.get("columns");
                    if (!(columns instanceof Map<?, ?> map)) continue;
                    for (Object rawColumn : castMap(map).values()) {
                        if (!(rawColumn instanceof Map<?, ?> rawMap)) continue;
                        Map<String, Object> column = castMap(rawMap);
                        String ref = column.get("ref") instanceof String value ? value
                                : column.get("refProperty") instanceof String value ? value : null;
                        if (ref != null && column.get("relEnum") instanceof String enumName) {
                            refs.put(ref, enumName);
                        }
                    }
                }
            }
            result.put(dataName, refs);
        }
        return result;
    }

    /**
     * Assign an Enum to its owning system when the design gives us a single data owner.
     * Enums referenced by data in multiple systems remain in common.enums so the generated
     * Java package does not claim ownership that the design does not establish.
     */
    private static Map<String, String> enumPackages(List<Map<String, Object>> enumDefinitions,
                                                     List<Map<String, Object>> dataDefinitions,
                                                     List<Map<String, Object>> apiDefinitions,
                                                     List<Map<String, Object>> viewDefinitions,
                                                     String basePackage) {
        Map<String, Set<String>> owners = new HashMap<>();
        Map<String, Set<String>> dataOwners = new HashMap<>();
        for (Map<String, Object> definition : enumDefinitions) {
            String name = requiredText(definition, "name", "enum");
            owners.put(name, new LinkedHashSet<>());
            dataOwners.put(name, new LinkedHashSet<>());
            if (definition.get("system") instanceof String system && !system.isBlank()) {
                owners.get(name).add(systemSegment(system));
            }
        }
        for (Map<String, Object> definition : dataDefinitions) {
            String system = systemSegment(definition);
            for (String enumName : relEnums(definition)) if (owners.containsKey(enumName)) {
                owners.get(enumName).add(system);
                dataOwners.get(enumName).add(system);
            }
        }
        for (Map<String, Object> definition : apiDefinitions) {
            String system = systemSegment(definition);
            for (String enumName : relEnums(definition)) if (owners.containsKey(enumName)) owners.get(enumName).add(system);
        }
        for (Map<String, Object> definition : viewDefinitions) {
            String system = systemSegment(definition);
            for (String enumName : relEnums(definition)) if (owners.containsKey(enumName)) owners.get(enumName).add(system);
        }
        Map<String, String> packages = new HashMap<>();
        for (Map<String, Object> definition : enumDefinitions) {
            String name = requiredText(definition, "name", "enum");
            if (definition.get("system") instanceof String system && !system.isBlank()) {
                packages.put(name, basePackage + "." + systemSegment(system) + ".enums");
                continue;
            }
            Set<String> candidates = dataOwners.getOrDefault(name, Set.of());
            if (candidates.isEmpty()) candidates = owners.getOrDefault(name, Set.of());
            String owner = candidates.size() == 1 ? candidates.iterator().next() : "common";
            packages.put(name, basePackage + "." + owner + ".enums");
        }
        return packages;
    }

    private static Set<String> relEnums(Object node) {
        Set<String> result = new LinkedHashSet<>();
        collectRelEnums(node, result);
        return result;
    }

    private static void collectRelEnums(Object node, Set<String> result) {
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = castMap(raw);
            if (map.get("relEnum") instanceof String rel && !rel.isBlank()) result.add(rel);
            for (Object value : map.values()) collectRelEnums(value, result);
        } else if (node instanceof Collection<?> values) {
            for (Object value : values) collectRelEnums(value, result);
        }
    }

    private static Map<String, Map<String, Object>> indexByName(List<Map<String, Object>> definitions) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> definition : definitions) if (definition.get("name") instanceof String name) index.put(name, definition);
        return index;
    }

}
