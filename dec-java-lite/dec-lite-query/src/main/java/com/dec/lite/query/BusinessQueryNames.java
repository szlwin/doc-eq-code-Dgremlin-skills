package com.dec.lite.query;

import java.util.LinkedHashMap;
import java.util.Map;

/** Optional presentation aliases. Targets are canonical View/Directory names or IDs, and View relation paths. */
public record BusinessQueryNames(Map<String, String> views, Map<String, String> directories,
                                 Map<String, Map<String, String>> relations) {
    public BusinessQueryNames {
        views = Map.copyOf(views);
        directories = Map.copyOf(directories);
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        relations.forEach((view, aliases) -> copy.put(view, Map.copyOf(aliases)));
        relations = Map.copyOf(copy);
    }
    public static BusinessQueryNames canonical() { return new BusinessQueryNames(Map.of(), Map.of(), Map.of()); }
}
