package com.dec.lite.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One canonical DEC YAML document and its lossless parsed root. */
public record DecDocument(
        DecKind kind,
        String version,
        Path source,
        Map<String, Object> root,
        List<DecDesignNode> nodes) {

    public DecDocument {
        if (kind == null) throw new IllegalArgumentException("document kind is required");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("document version is required");
        if (source == null) throw new IllegalArgumentException("document source is required");
        root = CanonicalValues.map(root);
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
    }
}
