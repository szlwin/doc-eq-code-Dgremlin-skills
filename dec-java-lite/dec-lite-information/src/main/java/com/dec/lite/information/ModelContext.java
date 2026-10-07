package com.dec.lite.information;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mutable model snapshot with an explicit write guard and monotonic version. */
public final class ModelContext {
    @FunctionalInterface public interface WriteGuard { void check(String path); }
    private static final java.util.regex.Pattern INDEXED = java.util.regex.Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\[(\\d+)]");
    private Map<String, Object> values;
    private String version;
    private final WriteGuard guard;

    public ModelContext(Map<String, Object> values) { this(values, "v1", path -> { }); }
    public ModelContext(Map<String, Object> values, String version, WriteGuard guard) {
        this.values = deepMap(values == null ? Map.of() : values);
        this.version = version == null ? "v1" : version;
        this.guard = guard == null ? path -> { } : guard;
    }
    public String version() { return version; }
    public Map<String, Object> values() { return deepMap(values); }
    public ModelContext copy() { return new ModelContext(values, version, guard); }
    public ModelContext copyWithGuard(WriteGuard additional) {
        if (additional == null) return copy();
        return new ModelContext(values, version, path -> { guard.check(path); additional.check(path); });
    }
    public void replaceFrom(ModelContext other) { this.values = deepMap(other.values); this.version = increment(version); }
    public Object read(String path) { Object current = values; for (String part : path.split("\\.")) current = lookup(current, part); return current; }
    public void checkWrite(String path) { guard.check(path); }

    public Object write(String path, Object value, MutationSet mutations) {
        guard.check(path);
        String[] parts = path.split("\\.");
        Object current = values;
        for (int i = 0; i < parts.length - 1; i++) current = navigate(current, parts[i], path);
        String leaf = parts[parts.length - 1];
        Object old;
        java.util.regex.Matcher indexed = INDEXED.matcher(leaf);
        if (indexed.matches()) {
            if (!(current instanceof Map<?, ?> map) || !(map.get(indexed.group(1)) instanceof List<?> list)) throw new IllegalStateException("cannot write through non-list path: " + path);
            @SuppressWarnings("unchecked") List<Object> mutable = (List<Object>) list;
            old = mutable.set(Integer.parseInt(indexed.group(2)), value);
        } else {
            if (!(current instanceof Map<?, ?> map)) throw new IllegalStateException("cannot write through non-map path: " + path);
            @SuppressWarnings("unchecked") Map<String, Object> mutable = (Map<String, Object>) map;
            old = mutable.put(leaf, value);
        }
        if (mutations != null) mutations.add(new Mutation(path, old, value));
        return old;
    }
    private static Object navigate(Object current, String part, String path) {
        Object value = lookup(current, part);
        if (value == null) throw new IllegalStateException("cannot write through missing path: " + path);
        return value;
    }
    private static Object lookup(Object current, String part) {
        java.util.regex.Matcher indexed = INDEXED.matcher(part);
        if (indexed.matches()) {
            if (!(current instanceof Map<?, ?> map) || !(map.get(indexed.group(1)) instanceof List<?> list)) return null;
            int index = Integer.parseInt(indexed.group(2)); return index < list.size() ? list.get(index) : null;
        }
        return current instanceof Map<?, ?> map ? map.get(part) : null;
    }
    private static String increment(String value) { return value + "'"; }
    private static Map<String, Object> deepMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) result.put(entry.getKey(), deep(entry.getValue()));
        return result;
    }
    private static Object deep(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) copy.put(String.valueOf(entry.getKey()), deep(entry.getValue()));
            return copy;
        }
        if (value instanceof List<?> list) { List<Object> copy = new ArrayList<>(); list.forEach(item -> copy.add(deep(item))); return copy; }
        return value;
    }
}
