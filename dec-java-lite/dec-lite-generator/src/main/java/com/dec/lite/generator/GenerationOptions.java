package com.dec.lite.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** Technical layout choices, kept outside the canonical business design. */
public record GenerationOptions(Map<String, String> apiGroups) {
    public static final String VERSION = "dec-generation/v1";
    private static final String GROUP_PREFIX = "api.group.";

    public GenerationOptions {
        Objects.requireNonNull(apiGroups, "apiGroups");
        apiGroups.forEach((id, group) -> {
            if (id == null || !id.matches("[A-Za-z0-9_-]+"))
                throw new GenerationException("invalid API Design ID in generation options: " + id);
            if (group == null || !group.matches("[A-Z][A-Za-z0-9]*"))
                throw new GenerationException("API group must be a Java class prefix beginning with A-Z: " + id + "=" + group);
        });
        apiGroups = Map.copyOf(apiGroups);
    }

    public static GenerationOptions defaults() { return new GenerationOptions(Map.of()); }

    /** Strict UTF-8 Java properties; duplicate/unknown keys fail instead of silently changing layout. */
    public static GenerationOptions load(Path file) {
        Objects.requireNonNull(file, "file");
        Properties properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new GenerationException("duplicate generation option: " + key);
                return super.put(key, value);
            }
        };
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException failure) {
            throw new GenerationException("cannot load generation options: " + file, failure);
        }
        if (!VERSION.equals(properties.getProperty("version")))
            throw new GenerationException("generation options require version=" + VERSION);
        Map<String, String> groups = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.equals("version")) continue;
            if (!key.startsWith(GROUP_PREFIX)) throw new GenerationException("unknown generation option: " + key);
            groups.put(key.substring(GROUP_PREFIX.length()), properties.getProperty(key));
        }
        return new GenerationOptions(groups);
    }

    void validateApiIds(Set<String> ids) {
        for (String id : new java.util.TreeSet<>(apiGroups.keySet()))
            if (!ids.contains(id)) throw new GenerationException("generation option references unknown API Design ID: " + id);
    }

    String propertiesText() {
        StringBuilder text = new StringBuilder("version=" + VERSION + "\n");
        new TreeMap<>(apiGroups).forEach((id, group) -> text.append(GROUP_PREFIX).append(id).append('=').append(group).append('\n'));
        return text.toString();
    }
}
