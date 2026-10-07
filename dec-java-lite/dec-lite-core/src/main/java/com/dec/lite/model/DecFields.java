package com.dec.lite.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Common shape checks for already parsed canonical DEC nodes. */
public final class DecFields {
    private DecFields() { }

    public static Map<String, Object> map(Object value, String context,
                                          Function<String, ? extends RuntimeException> failure) {
        if (!(value instanceof Map<?, ?> raw)) throw failure.apply(context + " must be a mapping");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw failure.apply(context + " keys must be strings");
            result.put(key, entry.getValue());
        }
        return result;
    }

    public static List<?> list(Object value, String context,
                               Function<String, ? extends RuntimeException> failure) {
        if (!(value instanceof List<?> list)) throw failure.apply(context + " must be a list");
        return list;
    }

    public static String requiredText(Map<String, Object> value, String key, String context,
                                      Function<String, ? extends RuntimeException> failure) {
        if (value.get(key) instanceof String text && !text.isBlank()) return text;
        throw failure.apply(context + " requires " + key);
    }
}
