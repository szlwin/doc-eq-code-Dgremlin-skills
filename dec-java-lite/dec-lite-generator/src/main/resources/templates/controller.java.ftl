package ${package};

<#list imports as import>
import ${import};
</#list>
import ${servicePackage}.${className}Service;
<#if requestClass?? && requestClass?has_content>
import ${package?keep_before_last(".")}.dto.${requestClass};
</#if>
import ${package?keep_before_last(".")}.view.*;

@RestController
@Validated
public class ${className}Controller {
    private final ${className}Service service;

    public ${className}Controller(${className}Service service) {
        this.service = service;
    }

    @RequestMapping(path = "${url}", method = RequestMethod.${httpMethod})
    public <#if responseType == "void">void<#else>${responseType}</#if> ${methodName}(<#list controllerParams as param>
            ${param.annotation} ${param.type} ${param.name}<#if param_has_next>,</#if></#list>
    ) {
        <#if responseType == "void">service.${methodName}(<#list serviceCallArgs as arg>${arg}<#if arg_has_next>, </#if></#list>);
        <#else>return service.${methodName}(<#list serviceCallArgs as arg>${arg}<#if arg_has_next>, </#if></#list>);</#if>
    }
}
