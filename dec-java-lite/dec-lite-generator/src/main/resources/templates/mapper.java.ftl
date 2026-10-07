package ${package?keep_before_last(".")}.mapper;

import org.apache.ibatis.annotations.Mapper;
import ${package}.${className};

@Mapper
public interface ${className}Mapper {
    ${className} selectById(Long id);
}
