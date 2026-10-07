package com.dec.lite.generator;

import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Deterministic Java project generator for canonical API/Data/View/Enum documents. */
public final class GeneratorPipeline {
    private static final String DEFAULT_BASE_PACKAGE = "com.dec.generated";
    private static final String MARKER = ".dec-generated";
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[^A-Za-z0-9_$]");

    private final Configuration freemarker;

    public GeneratorPipeline() {
        freemarker = new Configuration(Configuration.VERSION_2_3_34);
        freemarker.setDefaultEncoding(StandardCharsets.UTF_8.name());
        freemarker.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        freemarker.setLogTemplateExceptions(false);
        freemarker.setFallbackOnNullLoopVariable(false);
    }

    public GenerationResult generate(DecProject project, Path output) {
        return generate(project, output, DEFAULT_BASE_PACKAGE);
    }

    public GenerationResult generate(DecProject project, Path output, String basePackage) {
        if (project == null) throw new IllegalArgumentException("project is required");
        if (output == null) throw new IllegalArgumentException("output is required");
        if (basePackage == null || basePackage.isBlank()) throw new IllegalArgumentException("base package is required");
        if (!basePackage.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
            throw new GenerationException("invalid Java base package: " + basePackage);
        if (!project.getDesignGaps().isEmpty()) {
            throw new GenerationException("cannot generate while DESIGN_GAP exists: "
                    + project.getDesignGaps().stream().map(Object::toString).collect(Collectors.joining("; ")));
        }

        Path target = output.toAbsolutePath().normalize();
        Context context = buildContext(project, basePackage);
        prepareOutput(target);
        List<String> generated = new ArrayList<>();
        try {
            writeTemplate(target, "pom.xml.ftl", context.projectModel(), "pom.xml", generated);
            writeTemplate(target, "datasources.properties.ftl", Map.of("dataSources", context.dataSources()),
                    "src/main/resources/dec/datasources.properties", generated);
            writeTemplate(target, "application.java.ftl", context.applicationModel(),
                    javaPath(context.basePackage(), "GeneratedApplication.java"), generated);
            for (Map<String, Object> model : context.entities()) {
                writeTemplate(target, "entity.java.ftl", model,
                        javaPath(context.basePackage(), "entity/" + model.get("className") + ".java"), generated);
            }
            for (Map<String, Object> model : context.enums()) {
                writeTemplate(target, "enum.java.ftl", model,
                        javaPath(context.basePackage(), "enums/" + model.get("className") + ".java"), generated);
            }
            for (Map<String, Object> model : context.views()) {
                writeTemplate(target, "view.java.ftl", model,
                        javaPath(context.basePackage(), "view/" + model.get("className") + ".java"), generated);
            }
            for (Map<String, Object> model : context.requests()) {
                writeTemplate(target, "request.java.ftl", model,
                        javaPath(context.basePackage(), "dto/" + model.get("className") + ".java"), generated);
            }
            for (Map<String, Object> model : context.apis()) {
                writeTemplate(target, "controller.java.ftl", model,
                        javaPath(context.basePackage(), "controller/" + model.get("className") + "Controller.java"), generated);
                writeTemplate(target, "service.java.ftl", model,
                        javaPath(context.basePackage(), "service/" + model.get("className") + "Service.java"), generated);
            }
            for (Map<String, Object> model : context.entities()) {
                writeTemplate(target, "mapper.java.ftl", model,
                        javaPath(context.basePackage(), "mapper/" + model.get("className") + "Mapper.java"), generated);
                writeTemplate(target, "dao.java.ftl", model,
                        javaPath(context.basePackage(), "dao/" + model.get("className") + "Dao.java"), generated);
            }
            writeManifest(target, generated);
        } catch (IOException | TemplateException e) {
            throw new GenerationException("cannot generate Java project in " + target, e);
        }
        return new GenerationResult(target, List.copyOf(generated));
    }

    private Context buildContext(DecProject project, String basePackage) {
        List<Map<String, Object>> dataDefinitions = topLevelEntries(project, DecKind.DATA, "datas");
        List<Map<String, Object>> enumDefinitions = topLevelEntries(project, DecKind.ENUM, "enums");
        List<Map<String, Object>> viewDefinitions = topLevelEntries(project, DecKind.VIEW, "views");
        List<Map<String, Object>> apiDefinitions = topLevelEntries(project, DecKind.API, "apis");
        Map<String, Map<String, Object>> dataByName = indexByName(dataDefinitions);
        Map<String, String> dataSources = dataSources(project);
        validateDataSources(dataDefinitions, dataSources);
        Map<String, Map<String, String>> dataEnumRefs = dataEnumRefs(dataDefinitions);
        Map<String, String> enumTypes = new HashMap<>();
        for (Map<String, Object> definition : enumDefinitions) {
            String name = requiredText(definition, "name", "enum");
            enumTypes.put(name, className(name));
        }
        validateDataDefinitions(dataDefinitions, dataSources, enumTypes);
        Map<String, String> viewTypes = new HashMap<>();
        for (Map<String, Object> definition : viewDefinitions) {
            String name = requiredText(definition, "name", "view");
            viewTypes.put(name, className(name));
        }

        List<Map<String, Object>> entities = new ArrayList<>();
        for (Map<String, Object> definition : dataDefinitions) entities.add(entityModel(definition, basePackage, enumTypes, dataEnumRefs));
        List<Map<String, Object>> enums = new ArrayList<>();
        for (Map<String, Object> definition : enumDefinitions) enums.add(enumModel(definition, basePackage));
        List<Map<String, Object>> views = new ArrayList<>();
        for (Map<String, Object> definition : viewDefinitions) {
            views.add(viewModel(definition, basePackage, dataByName, enumTypes, viewTypes, dataEnumRefs, views));
        }
        List<Map<String, Object>> requests = new ArrayList<>();
        List<Map<String, Object>> apis = new ArrayList<>();
        for (Map<String, Object> definition : apiDefinitions) {
            validateApiContract(definition, enumTypes, viewTypes);
            Map<String, Object> api = apiModel(definition, basePackage, enumTypes, viewTypes);
            Object request = api.remove("requestModel");
            if (request instanceof Map<?, ?> requestMap) requests.add(castMap(requestMap));
            Object response = api.remove("responseModel");
            if (response instanceof Map<?, ?> responseMap) views.add(castMap(responseMap));
            apis.add(api);
        }
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
        return new Context(basePackage, projectModel(basePackage), application, entities, enums, views, requests, apis, dataSourceModels);
    }

    private Map<String, Object> entityModel(Map<String, Object> definition, String basePackage,
                                             Map<String, String> enumTypes,
                                             Map<String, Map<String, String>> dataEnumRefs) {
        String name = requiredText(definition, "name", "data");
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("package", basePackage + ".entity");
        model.put("className", className(name));
        model.put("designId", requiredText(definition, "id", "data"));
        model.put("fields", fieldsFromProperties(definition.get("properties"), enumTypes, basePackage + ".enums",
                dataEnumRefs.getOrDefault(name, Map.of())));
        return model;
    }

    private Map<String, Object> enumModel(Map<String, Object> definition, String basePackage) {
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
            if (rawValue == null) throw new GenerationException("enum value " + enumName + " has no value");
            values.add(Map.of("name", constant, "value", javaString(String.valueOf(rawValue))));
        }
        return new LinkedHashMap<>(Map.of("package", basePackage + ".enums", "className", className(name),
                "designId", requiredText(definition, "id", "enum"), "values", values));
    }

    private Map<String, Object> viewModel(Map<String, Object> definition, String basePackage,
                                          Map<String, Map<String, Object>> dataByName,
                                          Map<String, String> enumTypes, Map<String, String> viewTypes,
                                          Map<String, Map<String, String>> dataEnumRefs,
                                          List<Map<String, Object>> projectionModels) {
        String name = requiredText(definition, "name", "view");
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("package", basePackage + ".view");
        model.put("className", className(name));
        model.put("designId", requiredText(definition, "id", "view"));
        model.put("fields", viewFields(definition.get("properties"), definition.get("targetMain"), dataByName,
                enumTypes, viewTypes, dataEnumRefs, basePackage, className(name), projectionModels));
        return model;
    }

    private Map<String, Object> apiModel(Map<String, Object> definition, String basePackage,
                                         Map<String, String> enumTypes, Map<String, String> viewTypes) {
        String name = requiredText(definition, "name", "api");
        String className = className(name);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("package", basePackage + ".controller");
        model.put("servicePackage", basePackage + ".service");
        model.put("className", className);
        model.put("designId", requiredText(definition, "id", "api"));
        model.put("methodName", methodName(name));
        model.put("url", requiredText(definition, "url", "api"));
        model.put("httpMethod", requiredText(definition, "method", "api"));
        model.put("responseType", responseType(definition, viewTypes));
        List<Map<String, Object>> controllerParams = new ArrayList<>();
        List<Map<String, Object>> serviceParams = new ArrayList<>();
        List<Map<String, Object>> bodyFields = new ArrayList<>();
        List<Map<String, Object>> expressionValidations = new ArrayList<>();
        Object requestRaw = definition.get("request");
        if (requestRaw instanceof Map<?, ?> requestMapRaw) {
            Map<String, Object> requestMap = castMap(requestMapRaw);
            Object paramsRaw = requestMap.get("params");
            if (paramsRaw instanceof Collection<?> params) {
                for (Object raw : params) {
                    Map<String, Object> param = castMap(raw);
                    String location = requiredText(param, "in", "api parameter");
                    String paramName = requiredText(param, "name", "api parameter");
                    requiredText(param, "type", "api parameter");
                    String type = javaType(param.get("type"), param.get("relEnum"), enumTypes);
                    boolean required = Boolean.TRUE.equals(param.get("required"));
                    if ("body".equals(location)) {
                        bodyFields.add(fieldFromApiParam(param, type, enumTypes, basePackage, expressionValidations));
                    } else {
                        Map<String, Object> controllerParam = new LinkedHashMap<>();
                        List<String> validations = validationAnnotations(param);
                        controllerParam.put("annotation", parameterAnnotation(location, paramName, required)
                                + (validations.isEmpty() ? "" : " " + String.join(" ", validations)));
                        controllerParam.put("imports", importsForApiParam(param, basePackage, enumTypes));
                        controllerParam.put("type", type);
                        controllerParam.put("name", javaIdentifier(paramName));
                        controllerParams.add(controllerParam);
                        Map<String, Object> serviceParam = new LinkedHashMap<>();
                        serviceParam.put("type", type);
                        serviceParam.put("name", javaIdentifier(paramName));
                        serviceParam.put("imports", importsForApiParam(param, basePackage, enumTypes));
                        serviceParams.add(serviceParam);
                    }
                }
            }
            Object validations = requestMap.get("validations");
            if (validations instanceof Collection<?> values) {
                if (bodyFields.isEmpty() && !values.isEmpty()) {
                    throw new GenerationException("DESIGN_GAP API " + name + " request expression requires body parameters in P2");
                }
                Map<String, String> fieldAccess = new LinkedHashMap<>();
                Set<String> enumFields = new LinkedHashSet<>();
                for (Map<String, Object> field : bodyFields) {
                    String canonicalName = String.valueOf(field.getOrDefault("canonicalName", field.get("name")));
                    String javaName = String.valueOf(field.get("name"));
                    if (Boolean.TRUE.equals(field.get("enumField"))) {
                        fieldAccess.put(canonicalName, "this." + javaName + " == null ? null : this." + javaName + ".getValue()");
                        enumFields.add(canonicalName);
                    } else {
                        fieldAccess.put(canonicalName, "this." + javaName);
                    }
                }
                for (Object rawValidation : values) {
                    Map<String, Object> validation = castMap(rawValidation);
                    if (!"expression".equals(validation.get("type"))) {
                        throw new GenerationException("DESIGN_GAP unsupported request validation type: " + validation.get("type"));
                    }
                    String expression = requiredText(validation, "expression", "request validation");
                    expressionValidations.add(Map.of(
                            "javaExpression", ExpressionCompiler.compile(expression, fieldAccess, enumFields),
                            "message", javaString(validation.get("message") instanceof String message ? message : expression)));
                }
            }
        }
        String requestClass = null;
        if (!bodyFields.isEmpty()) {
            requestClass = className + "Request";
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("package", basePackage + ".dto");
            request.put("className", requestClass);
            request.put("designId", definition.get("id"));
            request.put("fields", bodyFields);
            request.put("expressions", expressionValidations);
            request.put("imports", importsForFields(bodyFields, !expressionValidations.isEmpty()));
            model.put("requestModel", request);
            Map<String, Object> bodyParam = new LinkedHashMap<>();
            bodyParam.put("annotation", "@Valid @RequestBody");
            bodyParam.put("type", requestClass);
            bodyParam.put("name", "request");
            controllerParams.add(0, bodyParam);
            serviceParams.add(0, Map.of("type", requestClass, "name", "request"));
        }
        model.put("controllerParams", controllerParams);
        model.put("serviceParams", serviceParams);
        model.put("serviceImports", importsForServiceParams(serviceParams, basePackage, enumTypes,
                String.valueOf(model.get("responseType"))));
        model.put("serviceCallArgs", serviceParams.stream().map(item -> item.get("name")).toList());
        model.put("requestClass", requestClass);
        Object responseRaw = definition.get("response");
        if (responseRaw instanceof Map<?, ?> rawResponse) {
            Map<String, Object> response = castMap(rawResponse);
            if (!(response.get("modelRef") instanceof String)) {
                Object rawFields = response.get("fields");
                if (rawFields instanceof Collection<?>) {
                    List<Map<String, Object>> fields = new ArrayList<>();
                    for (Object rawField : (Collection<?>) rawFields) {
                        Map<String, Object> field = castMap(rawField);
                        String fieldName = requiredText(field, "name", "api response field");
                        requiredText(field, "type", "api response field");
                        if (Boolean.TRUE.equals(field.get("required"))
                                || (field.get("validations") instanceof Collection<?> validations && !validations.isEmpty())) {
                            throw new GenerationException("DESIGN_GAP response field constraints are not implemented: " + name + "." + fieldName);
                        }
                        String type = javaType(field.get("type"), field.get("relEnum"), enumTypes);
                        List<String> imports = new ArrayList<>();
                        if (field.get("relEnum") instanceof String rel && enumTypes.containsKey(rel)) {
                            imports.add(basePackage + ".enums." + enumTypes.get(rel));
                        }
                        fields.add(Map.of("name", javaIdentifier(fieldName), "type", type,
                                "annotations", List.of(), "imports", imports));
                    }
                    model.put("responseModel", Map.of(
                            "package", basePackage + ".view",
                            "className", model.get("responseType"),
                            "designId", model.get("designId"),
                            "fields", fields));
                }
            }
        }
        model.put("imports", controllerImports(controllerParams));
        return model;
    }

    private String responseType(Map<String, Object> definition, Map<String, String> viewTypes) {
        Object responseRaw = definition.get("response");
        if (!(responseRaw instanceof Map<?, ?> raw)) return "void";
        Map<String, Object> response = castMap(raw);
        String modelRef = response.get("modelRef") instanceof String ref ? ref : null;
        if (modelRef != null && viewTypes.containsKey(modelRef)) return viewTypes.get(modelRef);
        if (modelRef != null) throw new GenerationException("DESIGN_GAP API response modelRef is unresolved: " + modelRef);
        if (response.get("fields") instanceof Collection<?>) return className(requiredText(definition, "name", "api") + "Response");
        return "void";
    }

    private static void validateApiContract(Map<String, Object> definition, Map<String, String> enumTypes,
                                            Map<String, String> viewTypes) {
        String apiName = requiredText(definition, "name", "api");
        Object requestRaw = definition.get("request");
        Set<String> pathPlaceholders = new LinkedHashSet<>();
        String url = requiredText(definition, "url", "api");
        java.util.regex.Matcher matcher = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}").matcher(url);
        while (matcher.find()) pathPlaceholders.add(matcher.group(1));
        Set<String> pathParams = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        if (requestRaw instanceof Map<?, ?> rawRequest) {
            Object paramsRaw = castMap(rawRequest).get("params");
            if (paramsRaw instanceof Collection<?> params) {
                for (Object raw : params) {
                    Map<String, Object> param = castMap(raw);
                    String name = requiredText(param, "name", "api parameter");
                    if (!names.add(name)) throw new GenerationException("DESIGN_GAP duplicate API parameter: " + apiName + "." + name);
                    String location = requiredText(param, "in", "api parameter");
                    if ("path".equals(location)) {
                        pathParams.add(name);
                        if (!Boolean.TRUE.equals(param.get("required"))) {
                            throw new GenerationException("DESIGN_GAP path parameter must be required: " + apiName + "." + name);
                        }
                    }
                    if (param.containsKey("default")) {
                        throw new GenerationException("DESIGN_GAP API default values are not implemented in P2: " + apiName + "." + name);
                    }
                    if (param.get("relEnum") instanceof String rel && !enumTypes.containsKey(rel)) {
                        throw new GenerationException("DESIGN_GAP unresolved relEnum: " + rel);
                    }
                    if (param.get("relEnum") instanceof String && hasValidationType(param.get("validations"), "enum")) {
                        throw new GenerationException("DESIGN_GAP API parameter cannot use relEnum and inline enum together: " + apiName + "." + name);
                    }
                    if (!"body".equals(location) && hasValidationType(param.get("validations"), "enum")) {
                        throw new GenerationException("DESIGN_GAP inline enum on non-body parameter is not implemented: " + apiName + "." + name);
                    }
                }
            }
        }
        if (!pathPlaceholders.equals(pathParams)) {
            throw new GenerationException("DESIGN_GAP API path parameters do not match URL: " + apiName);
        }
        Object responseRaw = definition.get("response");
        if (responseRaw instanceof Map<?, ?> responseMap) {
            Map<String, Object> response = castMap(responseMap);
            if (response.get("modelRef") instanceof String modelRef && !viewTypes.containsKey(modelRef)) {
                throw new GenerationException("DESIGN_GAP API response modelRef is unresolved: " + modelRef);
            }
        }
    }

    private static void validateDataDefinitions(List<Map<String, Object>> definitions,
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

    private static boolean hasValidationType(Object raw, String type) {
        if (!(raw instanceof Collection<?> collection)) return false;
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map && type.equals(castMap(map).get("type"))) return true;
        }
        return false;
    }

    private List<Map<String, Object>> fieldsFromProperties(Object properties, Map<String, String> enumTypes, String enumPackage,
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
            if (relEnum instanceof String rel && enumTypes.containsKey(rel)) imports.add(enumPackage + "." + enumTypes.get(rel));
            field.put("imports", imports);
            fields.add(field);
        }
        return fields;
    }

    private List<Map<String, Object>> viewFields(Object properties, Object targetMain,
                                                  Map<String, Map<String, Object>> dataByName,
                                                  Map<String, String> enumTypes, Map<String, String> viewTypes,
                                                  Map<String, Map<String, String>> dataEnumRefs,
                                                  String basePackage, String parentClass,
                                                  List<Map<String, Object>> projectionModels) {
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
                Map<String, Object> child = dataByName.get(childData);
                if (child == null) throw new GenerationException("DESIGN_GAP view relation references unknown data " + childData);
                validateRelation(property, child, targetMain, dataByName);
                String childType = parentClass + className(entry.getKey());
                List<Map<String, Object>> childFields = viewFields(property.get("properties"), childData,
                        dataByName, enumTypes, viewTypes, dataEnumRefs, basePackage, childType, projectionModels);
                projectionModels.add(Map.of("package", basePackage + ".view", "className", childType,
                        "designId", parentClass + "." + entry.getKey(), "fields", childFields));
                type = "one-to-many".equals(relation) ? "List<" + childType + ">" : childType;
                if ("one-to-many".equals(relation)) imports.add("java.util.List");
            } else {
                String ref = property.get("ref") instanceof String text ? text : fieldName;
                String dataName = property.get("data") instanceof String text ? text : String.valueOf(targetMain);
                Map<String, Object> data = dataName == null ? null : dataByName.get(dataName);
                if (data == null) throw new GenerationException("DESIGN_GAP view references unknown data " + dataName);
                Object dataProperties = data == null ? null : data.get("properties");
                Object rawType = dataProperties instanceof Map<?, ?> map ? castMap(map).get(ref) : null;
                if (rawType == null) throw new GenerationException("DESIGN_GAP view property references unknown data property " + dataName + "." + ref);
                Map<String, Object> rawProperty = rawType instanceof Map<?, ?> map ? castMap(map) : Map.of("type", rawType == null ? "object" : rawType);
                Object relEnum = property.get("relEnum");
                if (relEnum == null) relEnum = rawProperty.get("relEnum");
                if (relEnum == null) relEnum = dataEnumRefs.getOrDefault(dataName, Map.of()).get(ref);
                type = javaType(rawProperty.get("type"), relEnum, enumTypes);
                imports.addAll(importsForJavaType(type));
                if (relEnum instanceof String rel && enumTypes.containsKey(rel)) {
                    imports.add(basePackage + ".enums." + enumTypes.get(rel));
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

    private Map<String, Object> fieldFromApiParam(Map<String, Object> param, String type,
                                                  Map<String, String> enumTypes, String basePackage,
                                                  List<Map<String, Object>> expressionValidations) {
        List<String> annotations = validationAnnotations(param);
        Object validations = param.get("validations");
        if (validations instanceof Collection<?> values) {
            for (Object raw : values) {
                Map<String, Object> validation = castMap(raw);
                String validationType = String.valueOf(validation.get("type"));
                switch (validationType) {
                    case "notNull", "notEmpty", "min", "max", "minLength", "maxLength", "regex", "pattern" -> { }
                    case "enum" -> {
                        Object enumValues = validation.get("values");
                        if (!(enumValues instanceof Collection<?> collection) || collection.isEmpty()) {
                            throw new GenerationException("DESIGN_GAP inline enum requires values for " + param.get("name"));
                        }
                        String fieldName = javaIdentifier(String.valueOf(param.get("name")));
                        String alternatives = collection.stream()
                                .map(item -> "Objects.equals(this." + fieldName + ", " + javaLiteral(item) + ")")
                                .collect(Collectors.joining(" || "));
                        expressionValidations.add(Map.of("javaExpression", "(" + alternatives + ")",
                                "message", javaString("invalid value for " + param.get("name"))));
                    }
                    default -> throw new GenerationException("DESIGN_GAP unsupported API validation " + validationType + " for " + param.get("name"));
                }
            }
        }
                        List<String> imports = new ArrayList<>(importsForJavaType(type));
        if (param.get("relEnum") instanceof String rel && enumTypes.containsKey(rel)) {
            imports.add(basePackage + ".enums." + enumTypes.get(rel));
        }
        String canonicalName = String.valueOf(param.get("name"));
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", javaIdentifier(canonicalName));
        field.put("canonicalName", canonicalName);
        field.put("enumField", param.get("relEnum") instanceof String);
        field.put("type", type);
        field.put("annotations", annotations);
        field.put("imports", imports);
        return field;
    }

    private static List<String> validationAnnotations(Map<String, Object> param) {
        List<String> annotations = new ArrayList<>();
        if (Boolean.TRUE.equals(param.get("required"))) addAnnotation(annotations, "@NotNull");
        Object raw = param.get("validations");
        if (!(raw instanceof Collection<?> validations)) return annotations;
        for (Object item : validations) {
            Map<String, Object> validation = castMap(item);
            String type = String.valueOf(validation.get("type"));
            Object value = validation.get("value");
            switch (type) {
                case "notNull" -> addAnnotation(annotations, "@NotNull");
                case "notEmpty" -> addAnnotation(annotations, "@NotEmpty");
                case "min", "max", "minLength", "maxLength" -> {
                    if (!(value instanceof Number number) || number.longValue() < 0 || number.doubleValue() != number.longValue()) {
                        throw new GenerationException("DESIGN_GAP " + type + " requires a non-negative integer for " + param.get("name"));
                    }
                    String annotation = switch (type) {
                        case "min" -> "@Min(" + number.longValue() + ")";
                        case "max" -> "@Max(" + number.longValue() + ")";
                        case "minLength" -> "@Size(min = " + number.longValue() + ")";
                        default -> "@Size(max = " + number.longValue() + ")";
                    };
                    addAnnotation(annotations, annotation);
                }
                case "regex", "pattern" -> {
                    if (!(value instanceof String pattern)) throw new GenerationException("DESIGN_GAP regex requires string value for " + param.get("name"));
                    addAnnotation(annotations, "@Pattern(regexp = " + javaString(pattern) + ")");
                }
                case "enum" -> { }
                default -> throw new GenerationException("DESIGN_GAP unsupported API validation " + type + " for " + param.get("name"));
            }
        }
        return annotations;
    }

    private static List<String> controllerImports(List<Map<String, Object>> params) {
        Set<String> imports = new LinkedHashSet<>(List.of(
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.RequestMethod",
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.web.bind.annotation.PathVariable",
                "org.springframework.web.bind.annotation.RequestParam",
                "org.springframework.web.bind.annotation.RequestHeader",
                "org.springframework.web.bind.annotation.RequestBody",
                "org.springframework.validation.annotation.Validated"));
        if (params.stream().anyMatch(param -> String.valueOf(param.get("annotation")).contains("@Valid"))) imports.add("jakarta.validation.Valid");
        if (params.stream().anyMatch(param -> String.valueOf(param.get("annotation")).matches(".*@(NotNull|NotEmpty|Min|Max|Size|Pattern).*$"))) {
            imports.add("jakarta.validation.constraints.*");
        }
        for (Map<String, Object> param : params) {
            Object rawImports = param.get("imports");
            if (rawImports instanceof Collection<?> values) values.forEach(value -> imports.add(String.valueOf(value)));
        }
        return List.copyOf(imports);
    }

    private static List<String> importsForApiParam(Map<String, Object> param, String basePackage,
                                                   Map<String, String> enumTypes) {
        Object relEnum = param.get("relEnum");
        if (relEnum instanceof String name && enumTypes.containsKey(name)) {
            return List.of(basePackage + ".enums." + enumTypes.get(name));
        }
        String type = String.valueOf(param.get("type")).toLowerCase(Locale.ROOT);
        if ("date".equals(type)) return List.of("java.time.LocalDate");
        if ("datetime".equals(type) || "timestamp".equals(type)) return List.of("java.time.LocalDateTime");
        if ("decimal".equals(type) || "bigdecimal".equals(type)) return List.of("java.math.BigDecimal");
        return List.of();
    }

    private static List<String> importsForServiceParams(List<Map<String, Object>> params, String basePackage,
                                                        Map<String, String> enumTypes, String responseType) {
        Set<String> imports = new LinkedHashSet<>();
        for (Map<String, Object> param : params) {
            String type = String.valueOf(param.get("type"));
            imports.addAll(importsForJavaType(type));
            if (param.get("imports") instanceof Collection<?> values) values.forEach(value -> imports.add(String.valueOf(value)));
        }
        imports.addAll(importsForJavaType(responseType));
        return List.copyOf(imports);
    }

    private static List<String> importsForJavaType(String type) {
        return switch (type) {
            case "BigDecimal" -> List.of("java.math.BigDecimal");
            case "LocalDate" -> List.of("java.time.LocalDate");
            case "LocalDateTime" -> List.of("java.time.LocalDateTime");
            default -> List.of();
        };
    }

    private static String parameterAnnotation(String location, String name, boolean required) {
        String escaped = javaString(name);
        return switch (location) {
            case "path" -> "@PathVariable(" + escaped + ")";
            case "query" -> "@RequestParam(value = " + escaped + ", required = " + required + ")";
            case "header" -> "@RequestHeader(value = " + escaped + ", required = " + required + ")";
            default -> throw new GenerationException("DESIGN_GAP unsupported API parameter location " + location);
        };
    }

    private static void addAnnotation(List<String> annotations, String annotation) {
        if (!annotations.contains(annotation)) annotations.add(annotation);
    }

    private static Map<String, Object> projectModel(String basePackage) {
        return Map.of("groupId", basePackage, "artifactId", "generated-dec-project", "version", "0.1.0");
    }

    private static List<Map<String, Object>> topLevelEntries(DecProject project, DecKind kind, String key) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != kind) continue;
            Object raw = document.root().get(key);
            if (raw instanceof Collection<?> collection) for (Object item : collection) result.add(castMap(item));
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

    private static Map<String, Map<String, Object>> indexByName(List<Map<String, Object>> definitions) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> definition : definitions) if (definition.get("name") instanceof String name) index.put(name, definition);
        return index;
    }

    private void writeTemplate(Path target, String templateName, Map<String, Object> model,
                               String relative, List<String> generated) throws IOException, TemplateException {
        Template template = freemarker.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        Path file = safePath(target, relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, writer.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        generated.add(relative.replace('\\', '/'));
    }

    private static void writeManifest(Path target, List<String> generated) throws IOException {
        List<String> sorted = generated.stream().distinct().sorted().toList();
        Files.writeString(target.resolve(MARKER), "dec-java-lite generated output\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.writeString(target.resolve("dec-generated-manifest.txt"), String.join("\n", sorted) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void prepareOutput(Path target) {
        try {
            Path systemTemp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            for (Path ancestor = target; ancestor != null; ancestor = ancestor.getParent()) {
                if (Files.isSymbolicLink(ancestor) && !(target.startsWith(systemTemp) && systemTemp.startsWith(ancestor)))
                    throw new GenerationException("output path contains a symbolic link: " + ancestor);
            }
            if (Files.exists(target) && !Files.isDirectory(target)) throw new GenerationException("output is not a directory: " + target);
            Files.createDirectories(target);
            try (var paths = Files.walk(target)) {
                if (paths.anyMatch(Files::isSymbolicLink)) throw new GenerationException("generated output contains a symbolic link: " + target);
            }
            try (var stream = Files.list(target)) {
                if (stream.findAny().isPresent() && !Files.exists(target.resolve(MARKER))) {
                    throw new GenerationException("refusing to overwrite non-generated directory: " + target);
                }
            }
            Path manifest = target.resolve("dec-generated-manifest.txt");
            if (Files.exists(target.resolve(MARKER)) && Files.exists(manifest)) {
                List<Path> staleFiles = new ArrayList<>();
                for (String relative : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                    if (relative.isBlank() || relative.contains("..") || relative.startsWith("/"))
                        throw new GenerationException("unsafe generated manifest entry: " + relative);
                    staleFiles.add(safePath(target, relative));
                }
                for (Path staleFile : staleFiles) Files.deleteIfExists(staleFile);
            }
        } catch (IOException e) {
            throw new GenerationException("cannot prepare output directory " + target, e);
        }
    }
    private static Path safePath(Path target, String relative) {
        Path path = Path.of(relative);
        Path resolved = target.resolve(path).normalize();
        if (path.isAbsolute() || !resolved.startsWith(target))
            throw new GenerationException("generated path escapes output directory: " + relative);
        return resolved;
    }

    private static String javaPath(String basePackage, String suffix) {
        return "src/main/java/" + basePackage.replace('.', '/') + "/" + suffix;
    }

    private static String requiredText(Map<String, Object> map, String key, String context) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new GenerationException(context + " requires " + key);
        return text;
    }

    private static String javaType(Object rawType, Object relEnum, Map<String, String> enumTypes) {
        if (relEnum instanceof String relation) {
            if (!enumTypes.containsKey(relation)) throw new GenerationException("DESIGN_GAP unresolved relEnum: " + relation);
            return enumTypes.get(relation);
        }
        String type = rawType == null ? "object" : String.valueOf(rawType).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "string", "text", "char" -> "String";
            case "int", "integer" -> "Integer";
            case "long" -> "Long";
            case "short" -> "Short";
            case "decimal", "bigdecimal" -> "BigDecimal";
            case "float" -> "Float";
            case "double" -> "Double";
            case "boolean", "bool" -> "Boolean";
            case "date" -> "LocalDate";
            case "datetime", "timestamp" -> "LocalDateTime";
            case "object", "map" -> "Object";
            default -> className(String.valueOf(rawType));
        };
    }

    private static List<String> importsForFields(List<Map<String, Object>> fields, boolean expressions) {
        Set<String> imports = new LinkedHashSet<>();
        for (Map<String, Object> field : fields) {
            Object type = field.get("type");
            if ("BigDecimal".equals(type)) imports.add("java.math.BigDecimal");
            if ("LocalDate".equals(type)) imports.add("java.time.LocalDate");
            if ("LocalDateTime".equals(type)) imports.add("java.time.LocalDateTime");
            Object raw = field.get("imports");
            if (raw instanceof Collection<?> collection) collection.forEach(item -> imports.add(String.valueOf(item)));
        }
        if (expressions) imports.add("java.util.Objects");
        return List.copyOf(imports);
    }

    private static String javaLiteral(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return String.valueOf(value);
        return javaString(String.valueOf(value));
    }

    private static String className(String value) {
        String[] parts = value.replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) if (!part.isBlank()) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        if (result.isEmpty()) result.append("Generated");
        if (Character.isDigit(result.charAt(0))) result.insert(0, '_');
        return result.toString();
    }

    private static String methodName(String value) {
        String clazz = className(value);
        return Character.toLowerCase(clazz.charAt(0)) + clazz.substring(1);
    }

    private static String javaIdentifier(String value) {
        String result = JAVA_IDENTIFIER.matcher(value).replaceAll("_");
        if (result.isBlank()) result = "value";
        if (Character.isDigit(result.charAt(0))) result = "_" + result;
        return result;
    }

    private static String enumConstant(String value) {
        String result = javaIdentifier(value).toUpperCase(Locale.ROOT);
        return result.isBlank() ? "VALUE" : result;
    }

    private static String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) throw new GenerationException("expected mapping but found " + value);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new GenerationException("YAML mapping key must be a string");
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> stringMap(Map<?, ?> value) {
        return castMap(value);
    }

    public record GenerationResult(Path output, List<String> files) { }

    private record Context(String basePackage, Map<String, Object> projectModel,
                           Map<String, Object> applicationModel,
                           List<Map<String, Object>> entities,
                           List<Map<String, Object>> enums,
                           List<Map<String, Object>> views,
                           List<Map<String, Object>> requests,
                           List<Map<String, Object>> apis,
                           List<Map<String, Object>> dataSources) { }
}
