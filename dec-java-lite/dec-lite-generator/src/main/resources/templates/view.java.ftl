package ${package};

<#assign imports=[]>
<#list fields as field>
<#list field.imports as import>
<#if !(imports?seq_contains(import))><#assign imports=imports + [import]></#if>
</#list>
</#list>
<#list imports as import>
import ${import};
</#list>

public class ${className} {
<#list fields as field>
    private ${field.type} ${field.name};
</#list>

<#list fields as field>
    public ${field.type} get${field.name?cap_first}() {
        return ${field.name};
    }

    public void set${field.name?cap_first}(${field.type} ${field.name}) {
        this.${field.name} = ${field.name};
    }
</#list>
}
