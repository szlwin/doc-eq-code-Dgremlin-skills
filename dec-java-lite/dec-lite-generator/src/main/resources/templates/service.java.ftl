package ${servicePackage};

<#list serviceImports as import>
import ${import};
</#list>

/** Application binding seam for API resource ${resource}. */
public interface ${className}Service {
<#list methods as api>
    /** DEC ${api.designId}; YAML ${api.designSource}#${api.designPath}; XML ${api.designXml} api[name=${api.designName}]. */
    ${api.responseType} ${api.methodName}(<#list api.serviceParams as param>${param.type} ${param.name}<#if param_has_next>, </#if></#list>);

</#list>
}
