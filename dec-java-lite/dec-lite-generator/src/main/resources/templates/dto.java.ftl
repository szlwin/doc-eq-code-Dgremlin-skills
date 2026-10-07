package ${package};

import jakarta.validation.constraints.*;

public class ${className}Request {

<#list fields as field>
<#if field.required>
@NotNull
</#if>
<#if field.regex??>
@Pattern(regexp="${field.regex}")
</#if>
private ${field.type} ${field.name};

</#list>
}
