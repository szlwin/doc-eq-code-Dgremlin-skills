package com.dec.lite.information;

import java.util.Objects;

/** Stable system-qualified Information identity. */
public record InformationKey(String system, String name) implements Comparable<InformationKey> {
    public InformationKey {
        system = required(system, "system");
        name = required(name, "name");
        if (system.contains(".") || name.contains(".")) {
            throw new IllegalArgumentException("Information key segments must not contain '.': " + system + "." + name);
        }
    }

    public static InformationKey parse(String value) {
        if (value == null || value.indexOf('.') <= 0 || value.indexOf('.') != value.lastIndexOf('.')) {
            throw new IllegalArgumentException("Information reference must be system.name: " + value);
        }
        return new InformationKey(value.substring(0, value.indexOf('.')), value.substring(value.indexOf('.') + 1));
    }

    @Override public int compareTo(InformationKey other) {
        int systemCompare = system.compareTo(other.system);
        return systemCompare != 0 ? systemCompare : name.compareTo(other.name);
    }

    @Override public String toString() { return system + "." + name; }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) throw new IllegalArgumentException(field + " must be non-blank");
        return value;
    }
}
