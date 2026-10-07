package com.dec.lite.action;

import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecKind;
import com.dec.lite.model.DecProject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit System-scoped model write policy compiled from systems.modelAccess. */
@FunctionalInterface
public interface SystemAccessPolicy {
    void checkWrite(String system, String path);
    static SystemAccessPolicy allowAll() { return (system, path) -> { }; }
    static SystemAccessPolicy compile(DecProject project) {
        Map<String, List<String>> writes = new LinkedHashMap<>();
        for (DecDocument document : project.getDocuments()) {
            if (document.kind() != DecKind.SYSTEMS) continue;
            for (Object rawSystem : list(document.root().get("systems"))) {
                Map<String, Object> system = map(rawSystem); String name = String.valueOf(system.get("name"));
                List<String> paths = writes.computeIfAbsent(name, ignored -> new ArrayList<>());
                for (Object rawAccess : list(system.get("modelAccess"))) {
                    Map<String, Object> access = map(rawAccess);
                    for (Object rawWrite : list(access.get("write"))) {
                        String path = String.valueOf(map(rawWrite).get("path"));
                        if (path.isBlank() || "null".equals(path)) throw new IllegalArgumentException("modelAccess.write.path is required for " + name);
                        paths.add(path);
                    }
                }
            }
        }
        return (system, path) -> {
            boolean allowed = writes.getOrDefault(system, List.of()).stream().anyMatch(prefix ->
                    "*".equals(prefix) || path.equals(prefix) || path.startsWith(prefix + ".") || path.startsWith(prefix + "["));
            if (!allowed) throw new SecurityException("ACCESS_DENIED system=" + system + " path=" + path);
        };
    }
    private static List<?> list(Object raw) { return raw instanceof List<?> values ? values : List.of(); }
    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) throw new IllegalArgumentException("systems.modelAccess must be mappings");
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
