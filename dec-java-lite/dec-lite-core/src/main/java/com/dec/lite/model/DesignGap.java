package com.dec.lite.model;

import java.nio.file.Path;

/** A semantic gap that prevents a design node from being safely generated. */
public record DesignGap(String code, DecKind kind, String designId, Path source, String message) {
    public DesignGap {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("gap code is required");
        if (kind == null) throw new IllegalArgumentException("gap kind is required");
        if (source == null) throw new IllegalArgumentException("gap source is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("gap message is required");
    }

    @Override
    public String toString() {
        String id = designId == null || designId.isBlank() ? "<missing-id>" : designId;
        return code + " [" + kind.value() + ":" + id + "] " + source + ": " + message;
    }
}
