package com.dec.lite.model;

import java.util.Locale;

/** Canonical DEC document kinds accepted by the parser. */
public enum DecKind {
    CONFIG("config"),
    DATA("data"),
    VIEW("view"),
    RULE("rule"),
    API("api"),
    ENUM("enum"),
    SYSTEMS("systems"),
    BUSINESS("business");

    private final String value;

    DecKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DecKind parse(Object raw) {
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing canonical DEC kind");
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        for (DecKind kind : values()) {
            if (kind.value.equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unsupported canonical DEC kind: " + text);
    }
}
