package ${package?keep_before_last(".")}.dao;

import org.springframework.stereotype.Repository;
import com.dec.lite.data.CrudDao;
import ${package}.${className};
import ${package?keep_before_last(".")}.mapper.${className}Mapper;

/** DEC ${designId}; YAML ${designSource}#${designPath}; XML ${designXml} data[name=${designName}]. */
@Repository
public class ${className}Dao extends CrudDao<${className}> {
    public ${className}Dao(${className}Mapper mapper) {
        super(mapper);
    }
}
