package ${servicePackage};

import org.springframework.stereotype.Service;
<#if requestClass?? && requestClass?has_content>
import ${package?keep_before_last(".")}.dto.${requestClass};
</#if>
<#if responseType != "void">
import ${package?keep_before_last(".")}.view.${responseType};
</#if>
<#list serviceImports as import>
import ${import};
</#list>

@Service
public class ${className}Service {
    public <#if responseType == "void">void<#else>${responseType}</#if> ${methodName}(<#list serviceParams as param>${param.type} ${param.name}<#if param_has_next>, </#if></#list>) {
        throw new UnsupportedOperationException("DESIGN_GAP ${designId}: application service is not bound to the DEC runtime");
    }
}
