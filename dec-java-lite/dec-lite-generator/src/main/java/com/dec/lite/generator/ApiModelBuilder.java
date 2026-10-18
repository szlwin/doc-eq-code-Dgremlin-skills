package com.dec.lite.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.dec.lite.generator.JavaModelSupport.*;

/** Builds API contracts, validation annotations and resource groups. */
final class ApiModelBuilder {
    static Map<String, Object> apiModel(Map<String, Object> definition, String basePackage,
                                         Map<String, String> enumTypes, Map<String, String> enumPackages,
                                         Map<String, String> viewTypes, Map<String, String> viewPackages, GenerationOptions options) {
        String name = requiredText(definition, "name", "api");
        String className = className(name);
        Map<String, Object> model = new LinkedHashMap<>();
        String systemPackage = systemPackage(definition, basePackage);
        model.put("package", systemPackage + ".controller");
        model.put("servicePackage", systemPackage + ".service");
        model.put("className", className);
        String configured = options.apiGroups().get(requiredText(definition, "id", "api"));
        String resource = configured == null ? apiResource(requiredText(definition, "url", "api")) : configured;
        model.put("resource", resource);
        model.put("groupClassName", configured == null ? className(resource) : configured);
        model.put("groupIdentity", (configured == null ? "resource:" : "configured:") + resource);
        model.put("designId", requiredText(definition, "id", "api"));
        model.put("designSource", definition.get("designSource"));
        model.put("designXml", definition.get("designXml"));
        model.put("designPath", definition.get("designPath"));
        model.put("designName", name);
        model.put("methodName", methodName(name));
        model.put("url", requiredText(definition, "url", "api"));
        model.put("httpMethod", requiredText(definition, "method", "api"));
        model.put("responseType", responseType(definition, viewTypes));
        String responseModelName = responseModelName(definition, viewTypes);
        if (!"void".equals(model.get("responseType")))
            model.put("responsePackage", responseModelName == null ? systemPackage + ".view" : viewPackages.get(responseModelName));
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
                        bodyFields.add(fieldFromApiParam(param, type, enumTypes, enumPackages, basePackage, expressionValidations));
                    } else {
                        Map<String, Object> controllerParam = new LinkedHashMap<>();
                        List<String> validations = validationAnnotations(param);
                        controllerParam.put("annotation", parameterAnnotation(location, paramName, required)
                                + (validations.isEmpty() ? "" : " " + String.join(" ", validations)));
                        controllerParam.put("imports", importsForApiParam(param, basePackage, enumTypes, enumPackages));
                        controllerParam.put("type", type);
                        controllerParam.put("name", javaIdentifier(paramName));
                        if (param.get("id") instanceof String id) controllerParam.put("designId", id);
                        controllerParam.put("designPath", definition.get("designPath") + ".request.params[id=" + param.get("id") + "]");
                        controllerParams.add(controllerParam);
                        Map<String, Object> serviceParam = new LinkedHashMap<>();
                        serviceParam.put("type", type);
                        serviceParam.put("name", javaIdentifier(paramName));
                        serviceParam.put("imports", importsForApiParam(param, basePackage, enumTypes, enumPackages));
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
            request.put("package", systemPackage + ".dto");
            request.put("className", requestClass);
            request.put("designId", definition.get("id"));
            request.put("designSource", definition.get("designSource"));
            request.put("designXml", definition.get("designXml"));
            request.put("designPath", definition.get("designPath"));
            request.put("designName", name);
            request.put("fields", bodyFields);
            request.put("expressions", expressionValidations);
            request.put("imports", importsForFields(bodyFields, !expressionValidations.isEmpty()));
            model.put("requestModel", request);
            model.put("requestPackage", systemPackage + ".dto");
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
                            imports.add(enumPackages.get(rel) + "." + enumTypes.get(rel));
                        }
                        fields.add(Map.of("name", javaIdentifier(fieldName), "type", type,
                                "annotations", List.of(), "imports", imports));
                    }
                    model.put("responseModel", Map.of(
                            "package", systemPackage + ".view",
                            "className", model.get("responseType"),
                            "designId", model.get("designId"),
                            "designSource", model.get("designSource"),
                            "designXml", model.get("designXml"),
                            "designPath", model.get("designPath") + ".response",
                            "designName", model.get("designName"),
                            "fields", fields));
                }
            }
        }
        model.put("imports", controllerImports(controllerParams));
        return model;
    }

    /** Group APIs by System package and URL resource, independent of YAML file and operation name. */
    private static String apiResource(String url) {
        for (String segment : url.split("/")) {
            if (segment.isBlank() || "api".equals(segment) || segment.matches("v[0-9]+")) continue;
            if (!segment.matches("[A-Za-z0-9_-]+"))
                throw new GenerationException("Cannot determine API resource from URL: " + url);
            return segment;
        }
        throw new GenerationException("API URL must contain a resource segment: " + url);
    }

    static List<Map<String, Object>> apiGroups(List<Map<String, Object>> apis) {
        Map<String, List<Map<String, Object>>> groups = new java.util.TreeMap<>();
        for (Map<String, Object> api : apis) {
            String key = api.get("package") + "." + api.get("groupClassName");
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(api);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (List<Map<String, Object>> methods : groups.values()) {
            methods.sort(java.util.Comparator.comparing(api -> String.valueOf(api.get("designId"))));
            Map<String, Object> first = methods.get(0);
            Set<String> names = new LinkedHashSet<>();
            Set<String> imports = new java.util.TreeSet<>();
            Set<String> serviceImports = new java.util.TreeSet<>();
            for (Map<String, Object> api : methods) {
                if (!first.get("groupIdentity").equals(api.get("groupIdentity")))
                    throw new GenerationException("API groups collide in Java class (configure all members explicitly to merge): " + api.get("groupClassName"));
                if (!names.add(String.valueOf(api.get("methodName"))))
                    throw new GenerationException("API methods collide in resource " + api.get("resource") + ": " + api.get("methodName"));
                for (Object value : (Collection<?>) api.get("imports")) imports.add(String.valueOf(value));
                for (Object value : (Collection<?>) api.get("serviceImports")) serviceImports.add(String.valueOf(value));
                if (api.get("requestClass") != null) serviceImports.add(api.get("requestPackage") + "." + api.get("requestClass"));
                String responseType = String.valueOf(api.get("responseType"));
                if (responseType.startsWith("List<")) {
                    serviceImports.add("java.util.List");
                    serviceImports.add(api.get("responsePackage") + "." + responseType.substring(5, responseType.length() - 1));
                } else if (!"void".equals(responseType)) serviceImports.add(api.get("responsePackage") + "." + responseType);
            }
            imports.addAll(serviceImports);
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("package", first.get("package"));
            group.put("servicePackage", first.get("servicePackage"));
            group.put("className", first.get("groupClassName"));
            group.put("resource", first.get("resource"));
            group.put("methods", methods);
            group.put("imports", List.copyOf(imports));
            group.put("serviceImports", List.copyOf(serviceImports));
            result.add(group);
        }
        return result;
    }

    private static String responseType(Map<String, Object> definition, Map<String, String> viewTypes) {
        Object responseRaw = definition.get("response");
        if (!(responseRaw instanceof Map<?, ?> raw)) return "void";
        Map<String, Object> response = castMap(raw);
        String modelRef = response.get("modelRef") instanceof String ref ? ref : null;
        if (modelRef != null && viewTypes.containsKey(modelRef))
            return "array".equals(response.get("type")) ? "List<" + viewTypes.get(modelRef) + ">" : viewTypes.get(modelRef);
        if (modelRef != null) throw new GenerationException("DESIGN_GAP API response modelRef is unresolved: " + modelRef);
        if ("array".equals(response.get("type"))) throw new GenerationException("DESIGN_GAP array response requires a View modelRef");
        if (response.get("fields") instanceof Collection<?>) return className(requiredText(definition, "name", "api") + "Response");
        return "void";
    }

    private static String responseModelName(Map<String, Object> definition, Map<String, String> viewTypes) {
        Object responseRaw = definition.get("response");
        if (!(responseRaw instanceof Map<?, ?> raw)) return null;
        Object modelRef = castMap(raw).get("modelRef");
        return modelRef instanceof String ref && viewTypes.containsKey(ref) ? ref : null;
    }

    static void validateApiContract(Map<String, Object> definition, Map<String, String> enumTypes,
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

    private static Map<String, Object> fieldFromApiParam(Map<String, Object> param, String type,
                                                  Map<String, String> enumTypes, Map<String, String> enumPackages,
                                                  String basePackage,
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
                                "message", javaString(validationMessage(param, validation, "enum", null))));
                    }
                    default -> throw new GenerationException("DESIGN_GAP unsupported API validation " + validationType + " for " + param.get("name"));
                }
            }
        }
        List<String> imports = new ArrayList<>(importsForJavaType(type));
        if (param.get("relEnum") instanceof String rel && enumTypes.containsKey(rel)) {
            imports.add(enumPackages.get(rel) + "." + enumTypes.get(rel));
        }
        String canonicalName = String.valueOf(param.get("name"));
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", javaIdentifier(canonicalName));
        field.put("canonicalName", canonicalName);
        if (param.get("id") instanceof String id) field.put("designId", id);
        field.put("enumField", param.get("relEnum") instanceof String);
        field.put("type", type);
        field.put("annotations", annotations);
        field.put("imports", imports);
        return field;
    }

    private static List<String> validationAnnotations(Map<String, Object> param) {
        List<String> annotations = new ArrayList<>();
        Object raw = param.get("validations");
        List<Map<String, Object>> validations = new ArrayList<>();
        if (raw instanceof Collection<?> values) {
            for (Object item : values) validations.add(castMap(item));
        }
        Map<String, Object> explicitNotNull = validations.stream()
                .filter(validation -> "notNull".equals(validation.get("type")))
                .findFirst().orElse(null);
        if (Boolean.TRUE.equals(param.get("required"))) {
            addAnnotation(annotations, "@NotNull(message = "
                    + javaString(validationMessage(param, explicitNotNull, "notNull", null)) + ")");
        }
        for (Map<String, Object> validation : validations) {
            String type = String.valueOf(validation.get("type"));
            Object value = validation.get("value");
            switch (type) {
                case "notNull" -> {
                    if (!Boolean.TRUE.equals(param.get("required"))) {
                        addAnnotation(annotations, "@NotNull(message = "
                                + javaString(validationMessage(param, validation, type, value)) + ")");
                    }
                }
                case "notEmpty" -> addAnnotation(annotations, "@NotEmpty(message = "
                        + javaString(validationMessage(param, validation, type, value)) + ")");
                case "min", "max", "minLength", "maxLength" -> {
                    if (!(value instanceof Number number) || number.longValue() < 0 || number.doubleValue() != number.longValue()) {
                        throw new GenerationException("DESIGN_GAP " + type + " requires a non-negative integer for " + param.get("name"));
                    }
                    String message = javaString(validationMessage(param, validation, type, value));
                    String annotation = switch (type) {
                        case "min" -> "@Min(value = " + number.longValue() + ", message = " + message + ")";
                        case "max" -> "@Max(value = " + number.longValue() + ", message = " + message + ")";
                        case "minLength" -> "@Size(min = " + number.longValue() + ", message = " + message + ")";
                        default -> "@Size(max = " + number.longValue() + ", message = " + message + ")";
                    };
                    addAnnotation(annotations, annotation);
                }
                case "regex", "pattern" -> {
                    if (!(value instanceof String pattern)) throw new GenerationException("DESIGN_GAP regex requires string value for " + param.get("name"));
                    addAnnotation(annotations, "@Pattern(regexp = " + javaString(pattern) + ", message = "
                            + javaString(validationMessage(param, validation, type, value)) + ")");
                }
                case "enum" -> { }
                default -> throw new GenerationException("DESIGN_GAP unsupported API validation " + type + " for " + param.get("name"));
            }
        }
        return annotations;
    }

    private static String validationMessage(Map<String, Object> param, Map<String, Object> validation,
                                            String type, Object value) {
        if (validation != null && validation.get("message") instanceof String message && !message.isBlank()) {
            return message;
        }
        String label = param.get("desc") instanceof String desc && !desc.isBlank()
                ? desc : String.valueOf(param.get("name"));
        return switch (type) {
            case "notNull", "notEmpty" -> label + "不能为空";
            case "min" -> label + "最小为" + value;
            case "max" -> label + "最大为" + value;
            case "minLength" -> label + "长度最小为" + value;
            case "maxLength" -> label + "长度最大为" + value;
            case "regex", "pattern" -> label + "格式不正确";
            case "enum" -> label + "值不合法";
            default -> label + "校验不通过";
        };
    }

    private static List<String> controllerImports(List<Map<String, Object>> params) {
        Set<String> imports = new LinkedHashSet<>(List.of(
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.RequestMethod",
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.validation.annotation.Validated"));
        for (String annotation : List.of("PathVariable", "RequestParam", "RequestHeader", "RequestBody")) {
            if (params.stream().anyMatch(param -> String.valueOf(param.get("annotation")).contains("@" + annotation)))
                imports.add("org.springframework.web.bind.annotation." + annotation);
        }
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
                                                   Map<String, String> enumTypes,
                                                   Map<String, String> enumPackages) {
        Object relEnum = param.get("relEnum");
        if (relEnum instanceof String name && enumTypes.containsKey(name)) {
            return List.of(enumPackages.get(name) + "." + enumTypes.get(name));
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

}
