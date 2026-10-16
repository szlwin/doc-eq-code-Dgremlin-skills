package ${package};

import ${basePackage}.design.DecDesignMap.InformationNode;
import java.util.List;

/** Generated System-owned Information tree. Inspect ruleData/expression/changeData here; execute via InformationEngine. */
public final class ${className} {
    public static final String ID = ${id};
    public static final String NAME = ${name};
    public static final String SOURCE = ${source};

<#list nodes as node>
    public static final InformationNode ${node.symbol} = new InformationNode(
            ${node.id}, ${node.key}, ${node.source},
            ${node.viewRef}, ${node.ruleRef},
            ${node.ruleData},
            ${node.expression},
            ${node.changeData},
            List.of(<#list node.dependencies as dependency>${dependency}<#sep>, </#list>));

</#list>
    public static final List<InformationNode> ALL = List.of(<#list nodes as node>${node.symbol}<#sep>, </#list>);

    private ${className}() { }
}
