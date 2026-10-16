<#macro actions values>
List.of(<#list values as action>
                        new ActionNode(${action.id}, ${action.name}, ${action.source}, ${action.system}, ${action.rule}, ${action.customType},
                                List.of(<#list action.produces as produce>new ProduceNode(${produce.id}, ${produce.ref}, ${produce.information})<#sep>, </#list>))<#sep>,</#list>)</#macro>
package ${package};

import ${basePackage}.design.DecDesignMap.*;
import java.util.List;

/** Generated Business flow: ordered dependencies/actions/produces/change and execution/case/back edges.
 * Use DirectoryNode.name() with DirectoryWorkflowRunner; this class does not execute or duplicate the state machine. */
public final class ${className} {
    public static final String ID = ${id};
    public static final String NAME = ${name};
    public static final String SOURCE = ${source};

<#list nodes as directory>
    public static final DirectoryNode ${directory.symbol} = new DirectoryNode(
            ${directory.id}, ${directory.name}, ${directory.source}, ${directory.type},
            ${directory.information}, ${directory.model}, ${directory.root?c},
            List.of(<#list directory.dependencies as dependency>${dependency}<#sep>, </#list>),
            List.of(<#list directory.edges as edge>
                    new DirectoryEdge(${edge.rel}, ${edge.role}, ${edge.information}, ${edge.back}, <@actions values=edge.backActions/>)<#sep>,</#list>),
            <@actions values=directory.actions/>,
            ${directory.change});

</#list>
    public static final List<DirectoryNode> ALL = List.of(<#list nodes as node>${node.symbol}<#sep>, </#list>);

    private ${className}() { }
}
