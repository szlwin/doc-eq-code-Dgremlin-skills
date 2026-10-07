package com.dec.lite.directory;

import java.util.List;

public record PathPlan(String from, String target, List<String> directories, List<String> edgeKinds) {
    public PathPlan {
        directories = List.copyOf(directories);
        edgeKinds = List.copyOf(edgeKinds);
        if (directories.isEmpty() || edgeKinds.size() != directories.size() - 1) throw new IllegalArgumentException("invalid Directory path");
    }
}
