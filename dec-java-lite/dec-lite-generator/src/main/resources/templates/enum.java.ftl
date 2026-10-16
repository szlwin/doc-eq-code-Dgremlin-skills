package ${package};

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**<#if description?? && description?has_content> ${description}. </#if>DEC ${designId}; YAML ${designSource}#${designPath}; XML ${designXml} enum[name=${designName}]. */
public enum ${className} {
<#list values as value>
    /**<#if value.descriptionText?? && value.descriptionText?has_content> ${value.descriptionText}. </#if>DEC ${value.designId}; YAML ${designSource}#${value.designPath}; XML ${designXml} enum-value[name=${value.name}]. */
    ${value.name}(${value.value}, ${value.description})<#if value_has_next>,<#else>;</#if>
</#list>

    private final Integer value;
    private final String description;

    ${className}(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static ${className} fromValue(Integer value) {
<#list values as enumValue>
        if (${enumValue.name}.value.equals(value)) return ${enumValue.name};
</#list>
        throw new IllegalArgumentException("Unknown ${className} value: " + value);
    }
}
