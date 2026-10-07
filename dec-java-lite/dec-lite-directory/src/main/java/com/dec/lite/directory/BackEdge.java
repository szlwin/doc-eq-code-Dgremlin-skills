package com.dec.lite.directory;

import com.dec.lite.action.ActionDefinition;
import java.util.List;

/** Return from execution parent to its child with ordered compensation actions. */
public record BackEdge(String from, String to, List<ActionDefinition> actions) {
    public BackEdge { actions = List.copyOf(actions == null ? List.of() : actions); }
}
