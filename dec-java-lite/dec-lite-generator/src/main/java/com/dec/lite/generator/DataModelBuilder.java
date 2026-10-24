package com.dec.lite.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dec.lite.generator.JavaModelSupport.*;

/** Builds and validates Data, Enum and View template models. */
final class DataModelBuilder {
    private record ViewFieldsContext(Map<String, Map<String, Object>> dataByName,
                                     Map<String, String> enumTypes,
                                     Map<String, String> enumPackages,
                                     Map<String, Map<String, String>> dataEnumRefs,
                                     List<Map<String, Object>> projectionModels,
                                     String currentPackage,
                                     String parentClass,
                                     DesignMeta meta) {
        ViewFieldsContext child(String property, String childClass) {
            return new ViewFieldsContext(dataByName, enumTypes, enumPackages, dataEnumRefs, projectionModels,
                    currentPackage, childClass, meta.withId(parentClass + "." + property)
                            .withPathSuffix(".properties." + property));
        }
    }

    static Map<String, Object> entityModel(Map<String, Object> definition, String basePackage,
                                             Map<String, String> enumTypes, Map<String, String> enumPackages,
                                             Map<String, Map<String, String>> dataEnumRefs) {
        String name = requiredText(definition, "name", "data");
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("package", systemPackage(definition, basePackage) + ".entity");
        model.put("className", className(name));
        model.putAll(designMeta(definition, name, "").asModel());
        model.put("fields", fieldsFromProperties(definition.get("properties"), enumTypes, enumPackages,
                dataEnumRefs.getOrDefault(name, Map.of())));
        return model;
    }

    static Map<String, Object> enumModel(Map<String, Object> definition, Map<String, String> enumPackages) {
        String name = requiredText(definition, "name", "enum");
        List<Map<String, Object>> values = new ArrayList<>();
        Object rawValues = definition.get("values");
        if (!(rawValues instanceof Collection<?> collection)) throw new GenerationException("enum " + name + " requires list field values");
        Set<String> constants = new LinkedHashSet<>();
        for (Object raw : collection) {
            Map<String, Object> value = castMap(raw);
            String enumName = requiredText(value, "name", "enum value");
            String constant = enumConstant(enumName);
            if (!constants.add(constant)) throw new GenerationException("duplicate enum constant " + constant + " in " + name);
            Object rawValue = value.get("value");
            Integer enumValueNumber = requiredInteger(rawValue, "enum value " + enumName + ".value");
            Map<String, Object> enumValue = new LinkedHashMap<>();
            enumValue.put("name", constant);
            enumValue.put("value", enumValueNumber);
            String description = String.valueOf(value.getOrDefault("desc", ""));
            enumValue.put("description", javaString(description));
            enumValue.put("descriptionText", description);
            enumValue.put("designId", requiredText(value, "id", "enum value"));
            enumValue.put("designPath", definition.get("designPath") + ".values[id=" + enumValue.get("designId") + "]");
            values.add(enumValue);
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("package", enumPackages.get(name));
        model.put("className", className(name));
        model.putAll(designMeta(definition, name, "").asModel());
        model.put("description", String.valueOf(definition.getOrDefault("desc", "")));
        model.put("values", values);
        return model;
    }

    static Map<String, Object> viewModel(Map<String, Object> definition, String basePackage,
                                          Map<String, Map<String, Object>> dataByName,
                                          Map<String, String> enumTypes,
                                          Map<String, String> enumPackages, Map<String, String> viewTypes,
                                          Map<String, String> viewPackages,
                                          Map<String, Map<String, String>> dataEnumRefs,
                                          List<Map<String, Object>> projectionModels) {
        String name = requiredText(definition, "name", "view");
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("package", viewPackages.get(name));
        model.put("className", className(name));
        DesignMeta meta = designMeta(definition, name, "");
        model.putAll(meta.asModel());
        model.put("fields", viewFields(definition.get("properties"), definition.get("targetMain"),
                new ViewFieldsContext(dataByName, enumTypes, enumPackages, dataEnumRefs, projectionModels,
                        String.valueOf(model.get("package")), className(name), meta)));
        return model;
    }

    static void validateDataDefinitions(List<Map<String, Object>> definitions,
                                                Map<String, String> dataSources,
                                                Map<String, String> enumTypes) {
        for (Map<String, Object> data : definitions) {
            Map<String, Object> properties = data.get("properties") instanceof Map<?, ?> map ? castMap(map) : Map.of();
            Object tables = data.get("tables");
            if (!(tables instanceof Collection<?> collection)) continue;
            for (Object rawTable : collection) {
                Map<String, Object> table = castMap(rawTable);
                String source = table.get("dataSource") instanceof String value ? value : null;
                if (source == null || !dataSources.containsKey(source)) {
                    throw new GenerationException("DESIGN_GAP dataSource is not declared: " + source);
                }
                Object columns = table.get("columns");
                if (!(columns instanceof Map<?, ?> columnMap)) continue;
                for (Map.Entry<String, Object> entry : castMap(columnMap).entrySet()) {
                    String ref = entry.getValue() instanceof String text ? text : null;
                    if (entry.getValue() instanceof Map<?, ?> rawColumn) {
                        Map<String, Object> column = castMap(rawColumn);
                        ref = column.get("ref") instanceof String text ? text : (column.get("refProperty") instanceof String text ? text : null);
                        if (column.get("relEnum") instanceof String rel && !enumTypes.containsKey(rel)) {
                            throw new GenerationException("DESIGN_GAP unresolved column relEnum: " + rel);
                        }
                    }
                    if (ref == null || !properties.containsKey(ref)) {
                        throw new GenerationException("DESIGN_GAP data column references unknown property: " + data.get("name") + "." + ref);
                    }
                }
            }
        }
    }

    private static List<Map<String, Object>> fieldsFromProperties(Object properties, Map<String, String> enumTypes,
                                                            Map<String, String> enumPackages,
                                                            Map<String, String> dataEnumRefs) {
        if (!(properties instanceof Map<?, ?> raw)) return List.of();
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : stringMap(raw).entrySet()) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", javaIdentifier(entry.getKey()));
            Map<String, Object> property = entry.getValue() instanceof Map<?, ?> map ? castMap(map) : Map.of("type", entry.getValue());
            Object relEnum = property.get("relEnum");
            if (relEnum == null) relEnum = dataEnumRefs.get(entry.getKey());
            field.put("type", javaType(property.get("type"), relEnum, enumTypes));
            field.put("annotations", List.of());
            List<String> imports = new ArrayList<>(importsForJavaType(String.valueOf(field.get("type"))));
            if (relEnum instanceof String rel && enumTypes.containsKey(rel)) imports.add(enumPackages.get(rel) + "." + enumTypes.get(rel));
            field.put("imports", imports);
            fields.add(field);
        }
        return fields;
    }

    private static List<Map<String, Object>> viewFields(Object properties, Object targetMain,
                                                        ViewFieldsContext context) {
        if (!(properties instanceof Map<?, ?> raw)) return List.of();
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : stringMap(raw).entrySet()) {
            Map<String, Object> property = entry.getValue() instanceof Map<?, ?> map ? castMap(map) : Map.of("ref", entry.getValue());
            String fieldName = javaIdentifier(entry.getKey());
            String type;
            List<String> imports = new ArrayList<>();
            if (property.get("relation") instanceof String relation) {
                if (!"one-to-many".equals(relation) && !"one-to-one".equals(relation)) {
                    throw new GenerationException("DESIGN_GAP unsupported view relation: " + relation);
                }
                String childData = String.valueOf(property.get("data"));
                Map<String, Object> child = context.dataByName().get(childData);
                if (child == null) throw new GenerationException("DESIGN_GAP view relation references unknown data " + childData);
                validateRelation(property, child, targetMain, context.dataByName());
                String childType = context.parentClass() + className(entry.getKey());
                ViewFieldsContext childContext = context.child(entry.getKey(), childType);
                List<Map<String, Object>> childFields = viewFields(property.get("properties"), childData,
                        childContext);
                Map<String, Object> projection = new LinkedHashMap<>();
                projection.put("package", context.currentPackage());
                projection.put("className", childType);
                projection.putAll(childContext.meta().asModel());
                projection.put("fields", childFields);
                context.projectionModels().add(projection);
                type = "one-to-many".equals(relation) ? "List<" + childType + ">" : childType;
                if ("one-to-many".equals(relation)) imports.add("java.util.List");
            } else {
                String ref = property.get("ref") instanceof String text ? text : fieldName;
                String dataName = property.get("data") instanceof String text ? text : String.valueOf(targetMain);
                Map<String, Object> data = dataName == null ? null : context.dataByName().get(dataName);
                if (data == null) throw new GenerationException("DESIGN_GAP view references unknown data " + dataName);
                Object dataProperties = data == null ? null : data.get("properties");
                Object rawType = dataProperties instanceof Map<?, ?> map ? castMap(map).get(ref) : null;
                if (rawType == null) throw new GenerationException("DESIGN_GAP view property references unknown data property " + dataName + "." + ref);
                Map<String, Object> rawProperty = rawType instanceof Map<?, ?> map ? castMap(map) : Map.of("type", rawType == null ? "object" : rawType);
                Object relEnum = property.get("relEnum");
                if (relEnum == null) relEnum = rawProperty.get("relEnum");
                if (relEnum == null) relEnum = context.dataEnumRefs().getOrDefault(dataName, Map.of()).get(ref);
                type = javaType(rawProperty.get("type"), relEnum, context.enumTypes());
                imports.addAll(importsForJavaType(type));
                if (relEnum instanceof String rel && context.enumTypes().containsKey(rel)) {
                    imports.add(context.enumPackages().get(rel) + "." + context.enumTypes().get(rel));
                }
            }
            fields.add(Map.of("name", fieldName, "type", type, "annotations", List.of(), "imports", imports));
        }
        return fields;
    }

    private static void validateRelation(Map<String, Object> relation, Map<String, Object> child,
                                         Object targetMain, Map<String, Map<String, Object>> dataByName) {
        String childName = String.valueOf(child.get("name"));
        Object childProperties = child.get("properties");
        String key = relation.get("key") instanceof String value ? value : null;
        if (key == null || !(childProperties instanceof Map<?, ?> childPropertiesMap) || !castMap(childPropertiesMap).containsKey(key)) {
            throw new GenerationException("DESIGN_GAP relation key is not a property of " + childName + ": " + key);
        }
        String parentName = String.valueOf(targetMain);
        Map<String, Object> parent = dataByName.get(parentName);
        String relKey = relation.get("relKey") instanceof String value ? value : null;
        if (parent == null || relKey == null || !(parent.get("properties") instanceof Map<?, ?> parentProperties)
                || !castMap(parentProperties).containsKey(relKey)) {
            throw new GenerationException("DESIGN_GAP relation relKey is not a property of " + parentName + ": " + relKey);
        }
        if (!(relation.get("properties") instanceof Map<?, ?>)) {
            throw new GenerationException("DESIGN_GAP relation " + relation.get("data") + " requires nested properties");
        }
        Map<String, Object> nested = castMap(relation.get("properties"));
        for (Map.Entry<String, Object> entry : nested.entrySet()) {
            Map<String, Object> field = entry.getValue() instanceof Map<?, ?> nestedFieldMap ? castMap(nestedFieldMap) : Map.of("ref", entry.getValue());
            if (field.containsKey("relation")) {
                Map<String, Object> grandchild = dataByName.get(String.valueOf(field.get("data")));
                if (grandchild == null) throw new GenerationException("DESIGN_GAP nested view relation references unknown data " + field.get("data"));
                validateRelation(field, grandchild, childName, dataByName);
                continue;
            }
            String ref = field.get("ref") instanceof String value ? value : entry.getKey();
            if (!castMap(childProperties).containsKey(ref)) {
                throw new GenerationException("DESIGN_GAP relation property references unknown child property: " + childName + "." + ref + " value=" + field);
            }
        }
    }

}
