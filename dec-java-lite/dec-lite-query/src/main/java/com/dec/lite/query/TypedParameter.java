package com.dec.lite.query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TypedParameter(String name, String type, Object value, boolean sensitive) {
    public TypedParameter {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("invalid parameter name");
        value = normalize(name, value, type);
        type = type == null ? infer(value) : type;
    }
    private static Object normalize(String name, Object value, String type) {
        if (value instanceof Enum<?> enumeration) value = enumeration.name();
        if (value == null || type == null) return value;
        try {
            return switch (type.toLowerCase(java.util.Locale.ROOT)) {
                case "int", "integer", "long" -> value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
                case "decimal", "number" -> value instanceof java.math.BigDecimal decimal ? decimal : new java.math.BigDecimal(value.toString());
                case "date" -> value instanceof java.time.LocalDate ? value : java.time.LocalDate.parse(value.toString());
                case "timestamp", "datetime" -> value instanceof java.time.Instant || value instanceof java.time.LocalDateTime ? value : java.time.LocalDateTime.parse(value.toString());
                case "boolean" -> value instanceof Boolean ? value : switch (value.toString().toLowerCase(java.util.Locale.ROOT)) {
                    case "true" -> true; case "false" -> false; default -> throw new IllegalArgumentException("invalid boolean parameter");
                };
                default -> value.toString();
            };
        } catch (RuntimeException exception) { throw new IllegalArgumentException("invalid " + type + " parameter " + name, exception); }
    }
    private static String infer(Object value) {
        if (value == null) return "unknown";
        if (value instanceof Integer || value instanceof Long || value instanceof Short) return "integer";
        if (value instanceof Number) return "decimal";
        if (value instanceof LocalDate) return "date";
        if (value instanceof Instant || value instanceof LocalDateTime) return "timestamp";
        if (value instanceof Boolean) return "boolean";
        return "string";
    }
    @Override public String toString() { return name + ":" + type + "=" + (sensitive ? "***" : value); }
}
