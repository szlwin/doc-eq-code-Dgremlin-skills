package com.dec.lite.information;

public record Mutation(String path, Object oldValue, Object newValue) {
    public Mutation {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("mutation path is required");
    }
}
