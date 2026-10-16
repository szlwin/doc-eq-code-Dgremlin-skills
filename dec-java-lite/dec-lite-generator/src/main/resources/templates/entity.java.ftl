package ${package};

<#list fields as field>
<#list field.imports as import>
import ${import};
</#list>
</#list>

/** DEC ${designId}; YAML ${designSource}#${designPath}; XML ${designXml} data[name=${designName}]. */
public class ${className} {
<#list fields as field>
    /** YAML ${designSource}#${designPath}.properties.${field.name}; XML ${designXml} property[name=${field.name}]. */
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
