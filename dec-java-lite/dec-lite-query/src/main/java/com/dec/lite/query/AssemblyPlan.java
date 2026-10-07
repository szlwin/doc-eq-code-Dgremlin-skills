package com.dec.lite.query;

import java.util.List;

/** Stable row-to-model assembly contract for root and collection identity. */
public record AssemblyPlan(String rootIdAlias, List<String> collectionPaths) {
    public AssemblyPlan { collectionPaths = List.copyOf(collectionPaths); }
}
