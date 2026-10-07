package com.dec.lite.information;

import java.util.ArrayList;
import java.util.List;

public final class MutationSet {
    private final List<Mutation> mutations = new ArrayList<>();

    public void add(Mutation mutation) { mutations.add(mutation); }
    public List<Mutation> mutations() { return List.copyOf(mutations); }
    public boolean isEmpty() { return mutations.isEmpty(); }
    public boolean touches(String path) {
        return mutations.stream().anyMatch(m -> overlaps(m.path(), path));
    }
    public boolean touchesAny(java.util.Collection<String> paths) {
        return paths.stream().anyMatch(this::touches);
    }
    private static boolean overlaps(String left, String right) {
        String normalizedLeft = left.replaceAll("\\[[0-9]+\\]", "");
        String normalizedRight = right.replaceAll("\\[[0-9]+\\]", "");
        return normalizedLeft.equals(normalizedRight) || normalizedLeft.startsWith(normalizedRight + ".") || normalizedRight.startsWith(normalizedLeft + ".");
    }
}
