package ${package?keep_before_last(".")}.mapper;

import org.apache.ibatis.annotations.Mapper;
import ${package}.${className};

/** DEC ${designId} from ${designSource}; provide an adapter for the selected DataSource. */
@Mapper
public interface ${className}Mapper {
    ${className} selectById(Long id);
    void insert(${className} value);
    void update(${className} value);
}
