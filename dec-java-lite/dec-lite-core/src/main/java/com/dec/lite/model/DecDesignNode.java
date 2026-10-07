package com.dec.lite.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A canonical DEC node retained without losing fields unknown to the Java generator. */
public record DecDesignNode(
        DecKind kind,
        String designId,
        String name,
        String path,
        Path source,
        Map<String, Object> attributes) {

    public DecDesignNode {
        if (kind == null) throw new IllegalArgumentException("node kind is required");
        if (designId == null || designId.isBlank()) throw new IllegalArgumentException("node designId is required");
        if (path == null || path.isBlank()) throw new IllegalArgumentException("node path is required");
        if (source == null) throw new IllegalArgumentException("node source is required");
        attributes = CanonicalValues.map(attributes);
    }
}
