package ${package};

import jakarta.validation.constraints.*;
<#list imports as import>
import ${import};
</#list>

/** DEC ${designId}; YAML ${designSource}#${designPath}.request; XML ${designXml} api[name=${designName}]/request. */
public class ${className} {
<#list fields as field>
<#if field.designId??>    /** DEC ${field.designId}; YAML ${designSource}#${designPath}.request.params[id=${field.designId}]; XML ${designXml} parameter[name=${field.canonicalName}]. */
</#if>
<#list field.annotations as annotation>
    ${annotation}
</#list>
    private ${field.type} ${field.name};

    public ${field.type} get${field.name?cap_first}() {
        return ${field.name};
    }

    public void set${field.name?cap_first}(${field.type} ${field.name}) {
        this.${field.name} = ${field.name};
    }
</#list>
<#list expressions as expression>
    @AssertTrue(message = ${expression.message})
    public boolean isDecExpressionValid${expression_index}() {
        return ${expression.javaExpression};
    }

</#list>
}
