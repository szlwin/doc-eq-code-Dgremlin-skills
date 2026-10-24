package com.dec.lite.generator;

import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared Java naming, types and canonical map access; no I/O. */
final class JavaModelSupport {
    record DesignMeta(String id, Object source, Object xml, String path, String name) {
        Map<String, Object> asModel() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("designId", id);
            result.put("designSource", source);
            result.put("designXml", xml);
            result.put("designPath", path);
            result.put("designName", name);
            return result;
        }

        DesignMeta withId(String value) { return new DesignMeta(value, source, xml, path, name); }
        DesignMeta withPathSuffix(String suffix) {
            return new DesignMeta(id, source, xml, path == null ? null : path + suffix, name);
        }
    }

    static DesignMeta designMeta(Map<String, Object> definition, String name, String extraPath) {
        String path = definition.get("designPath") == null ? null : String.valueOf(definition.get("designPath"));
        if (path != null && extraPath != null) path += extraPath;
        return new DesignMeta(requiredText(definition, "id", "design"), definition.get("designSource"),
                definition.get("designXml"), path, name);
    }

    static final Pattern JAVA_IDENTIFIER = Pattern.compile("[^A-Za-z0-9_$]");
    static boolean hasValidationType(Object raw, String type) {
        if (!(raw instanceof Collection<?> collection)) return false;
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map && type.equals(castMap(map).get("type"))) return true;
        }
        return false;
    }

    static List<String> importsForJavaType(String type) {
        return switch (type) {
            case "BigDecimal" -> List.of("java.math.BigDecimal");
            case "LocalDate" -> List.of("java.time.LocalDate");
            case "LocalDateTime" -> List.of("java.time.LocalDateTime");
            default -> List.of();
        };
    }

    static String systemSegment(Map<String, Object> definition) {
        Object raw = definition.get("system");
        return raw instanceof String system && !system.isBlank() ? systemSegment(system) : "common";
    }

    static String systemSegment(String system) {
        String segment = system.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_$]", "_");
        if (!segment.matches("[a-z_$][a-z0-9_$]*"))
            throw new GenerationException("invalid system package segment: " + system);
        return segment;
    }

    static String javaPath(String basePackage, String suffix) {
        return "src/main/java/" + basePackage.replace('.', '/') + "/" + suffix;
    }

    static String siblingPackage(String packageName, String child) {
        int separator = packageName.lastIndexOf('.');
        if (separator < 0) throw new GenerationException("invalid generated package: " + packageName);
        return packageName.substring(0, separator + 1) + child;
    }

    static String systemPackage(Map<String, Object> definition, String basePackage) {
        Object raw = definition.get("system");
        String system = raw instanceof String value && !value.isBlank() ? value : "common";
        return basePackage + "." + systemSegment(system);
    }

    static String requiredText(Map<String, Object> map, String key, String context) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new GenerationException(context + " requires " + key);
        return text;
    }

    static String javaType(Object rawType, Object relEnum, Map<String, String> enumTypes) {
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

    static List<String> importsForFields(List<Map<String, Object>> fields, boolean expressions) {
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

    static String javaLiteral(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return String.valueOf(value);
        return javaString(String.valueOf(value));
    }

    static String className(String value) {
        String[] parts = value.replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) if (!part.isBlank()) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        if (result.isEmpty()) result.append("Generated");
        if (Character.isDigit(result.charAt(0))) result.insert(0, '_');
        return result.toString();
    }

    static String methodName(String value) {
        String clazz = className(value);
        return Character.toLowerCase(clazz.charAt(0)) + clazz.substring(1);
    }

    static String javaIdentifier(String value) {
        String result = JAVA_IDENTIFIER.matcher(value).replaceAll("_");
        if (result.isBlank()) result = "value";
        if (Character.isDigit(result.charAt(0))) result = "_" + result;
        return result;
    }

    static String enumConstant(String value) {
        String result = javaIdentifier(value).toUpperCase(Locale.ROOT);
        return result.isBlank() ? "VALUE" : result;
    }

    static String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    static Integer requiredInteger(Object value, String context) {
        if (value == null || value instanceof Boolean) {
            throw new GenerationException(context + " must be an Integer");
        }
        if (value instanceof BigInteger bigInteger) {
            if (bigInteger.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                    || bigInteger.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw new GenerationException(context + " must be an Integer");
            }
            return bigInteger.intValue();
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long integer = ((Number) value).longValue();
            if (integer < Integer.MIN_VALUE || integer > Integer.MAX_VALUE) {
                throw new GenerationException(context + " must be an Integer");
            }
            return (int) integer;
        }
        throw new GenerationException(context + " must be an Integer");
    }

    static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) throw new GenerationException("expected mapping but found " + value);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new GenerationException("YAML mapping key must be a string");
            result.put(key, entry.getValue());
        }
        return result;
    }

    static Map<String, Object> stringMap(Map<?, ?> value) {
        return castMap(value);
    }

}
