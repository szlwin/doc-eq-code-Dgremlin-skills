package ${package};

<#list fields as field>
<#list field.imports as import>
import ${import};
</#list>
</#list>

/** DEC ${designId} from ${designSource}. */
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
