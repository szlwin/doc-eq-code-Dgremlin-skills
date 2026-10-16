package ${package?keep_before_last(".")}.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.dec.lite.data.CrudMapper;
import ${package}.${className};

/** DEC ${designId}; YAML ${designSource}#${designPath}; XML ${designXml} data[name=${designName}]. */
@Mapper
public interface ${className}Mapper extends CrudMapper<${className}> { }
