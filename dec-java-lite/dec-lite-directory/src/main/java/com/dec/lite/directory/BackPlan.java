package com.dec.lite.directory;

import java.util.List;

public record BackPlan(String from, String target, List<String> directories, List<BackEdge> actions) {
    public BackPlan { directories = List.copyOf(directories); actions = List.copyOf(actions); }
}
