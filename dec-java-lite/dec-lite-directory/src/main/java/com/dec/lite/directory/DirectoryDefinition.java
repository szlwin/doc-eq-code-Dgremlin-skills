package com.dec.lite.directory;

import com.dec.lite.action.ActionDefinition;
import com.dec.lite.information.InformationKey;
import java.nio.file.Path;
import java.util.List;

public record DirectoryDefinition(String id, String name, String type, String modelRef,
                                  InformationKey informationRef, boolean root,
                                  List<InformationKey> dependencies, List<ActionDefinition> actions,
                                  InformationKey changeInformation, Path source) {
    public DirectoryDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Directory id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Directory name is required");
        if (modelRef == null || modelRef.isBlank()) throw new IllegalArgumentException("Directory modelRef is required");
        if (informationRef == null) throw new IllegalArgumentException("Directory informationRef is required");
        type = type == null ? "normal" : type;
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        actions = List.copyOf(actions == null ? List.of() : actions);
        if (source == null) throw new IllegalArgumentException("Directory source is required");
    }
    public boolean result() { return "result".equals(type); }
}
