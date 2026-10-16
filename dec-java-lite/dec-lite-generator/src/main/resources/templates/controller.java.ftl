package ${package};

<#list imports as import>
import ${import};
</#list>
import ${servicePackage}.${className}Service;

/** API resource ${resource}; each method retains its canonical design references. */
@RestController
@Validated
public class ${className}Controller {
    private final ${className}Service service;

    public ${className}Controller(${className}Service service) {
        this.service = service;
    }

<#list methods as api>
    /** DEC ${api.designId}; YAML ${api.designSource}#${api.designPath}; XML ${api.designXml} api[name=${api.designName}]. */
    @RequestMapping(path = "${api.url}", method = RequestMethod.${api.httpMethod})
    public <#if api.responseType == "void">void<#else>${api.responseType}</#if> ${api.methodName}(<#list api.controllerParams as param>
            <#if param.designId??>/* DEC ${param.designId}; YAML ${api.designSource}#${param.designPath}; XML ${api.designXml} parameter[name=${param.name}] */ </#if>${param.annotation} ${param.type} ${param.name}<#if param_has_next>,</#if></#list>
    ) {
        <#if api.responseType == "void">service.${api.methodName}(<#list api.serviceCallArgs as arg>${arg}<#if arg_has_next>, </#if></#list>);
        <#else>return service.${api.methodName}(<#list api.serviceCallArgs as arg>${arg}<#if arg_has_next>, </#if></#list>);</#if>
    }

</#list>
}
