package ${package?keep_before_last(".")}.dao;

import org.springframework.stereotype.Repository;
import ${package}.${className};
import ${package?keep_before_last(".")}.mapper.${className}Mapper;

@Repository
public class ${className}Dao {
    private final ${className}Mapper mapper;

    public ${className}Dao(${className}Mapper mapper) {
        this.mapper = mapper;
    }

    public ${className} find(Long id) {
        return mapper.selectById(id);
    }
}
