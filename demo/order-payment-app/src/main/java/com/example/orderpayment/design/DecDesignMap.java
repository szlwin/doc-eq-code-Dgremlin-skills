package com.example.orderpayment.design;

import java.util.List;

/** Immutable navigation types. Definitions are generated in System Information and Business Flow classes.
 * Recognition, materialization and execution remain owned by the DEC runtime. */
public final class DecDesignMap {
    private DecDesignMap() { }

    public record InformationNode(String id, String key, String source, String viewRef, String ruleRef,
                                  String ruleData, String expression, String changeData, List<String> dependencies) {
        public InformationNode { dependencies = List.copyOf(dependencies); }
    }

    public record DirectoryNode(String id, String name, String source, String type,
                                String informationRef, String modelRef, boolean root,
                                List<String> dependencies, List<DirectoryEdge> edges,
                                List<ActionNode> actions, String changeInformationRef) {
        public DirectoryNode {
            dependencies = List.copyOf(dependencies);
            edges = List.copyOf(edges);
            actions = List.copyOf(actions);
        }
    }

    /** execution: rel -> owning directory; case: owning directory -> rel. Back reverses execution. */
    public record DirectoryEdge(String rel, String role, String informationRef, String backName,
                                List<ActionNode> backActions) {
        public DirectoryEdge { backActions = List.copyOf(backActions); }
    }

    /** customType is the registry key for a Custom Action; null for a RuleView Action. */
    public record ActionNode(String id, String name, String source, String systemRef, String ruleRef, String customType,
                             List<ProduceNode> produces) {
        public ActionNode { produces = List.copyOf(produces); }
        public boolean isCustom() { return customType != null; }
    }

    public record ProduceNode(String id, String ref, String informationRef) { }
}
