package com.dec.lite.generator;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.LinkedHashMap;

/** One maintained set of dependency defaults for every generated POM. */
final class GenerationVersions {
    private GenerationVersions() { }
    static final Map<String, Object> DEFAULTS = load();

    private static Map<String, Object> load() {
        try (var stream = GenerationVersions.class.getResourceAsStream("/generation-versions.properties")) {
            if (stream == null) throw new GenerationException("Missing generation dependency defaults");
            var properties = new Properties();
            properties.load(stream);
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : java.util.List.of("springBootVersion", "mybatisVersion", "runtimeVersion", "javaVersion")) {
                String value = properties.getProperty(key);
                if (value == null || !value.matches("[0-9][A-Za-z0-9.\\-]*"))
                    throw new GenerationException("Invalid dependency version: " + key);
                result.put(key, value);
            }
            return Map.copyOf(result);
        } catch (IOException failure) {
            throw new GenerationException("Cannot read generation dependency defaults: " + failure.getMessage());
        }
    }
}
