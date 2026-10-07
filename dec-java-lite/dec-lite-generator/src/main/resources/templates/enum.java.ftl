package ${package};

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ${className} {
<#list values as value>
    ${value.name}(${value.value})<#if value_has_next>,<#else>;</#if>
</#list>

    private final String value;

    ${className}(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ${className} fromValue(String value) {
<#list values as enumValue>
        if (${enumValue.name}.value.equals(value)) return ${enumValue.name};
</#list>
        throw new IllegalArgumentException("Unknown ${className} value: " + value);
    }
}
