package com.dec.lite.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deeply immutable canonical values handed to compilers and running sessions. */
public final class CanonicalValues {
    private CanonicalValues() { }
    public static Map<String, Object> map(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> result.put(key, freeze(value)));
        return Collections.unmodifiableMap(result);
    }
    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nested) -> result.put(String.valueOf(key), freeze(nested)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(freeze(item)));
            return Collections.unmodifiableList(result);
        }
        return value;
    }
}
