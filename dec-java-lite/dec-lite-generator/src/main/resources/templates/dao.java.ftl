package ${package?keep_before_last(".")}.dao;

import org.springframework.stereotype.Repository;
import ${package}.${className};
import ${package?keep_before_last(".")}.mapper.${className}Mapper;

/** DEC ${designId} from ${designSource}; data access boundary. */
@Repository
public class ${className}Dao {
    private final ${className}Mapper mapper;

    public ${className}Dao(${className}Mapper mapper) {
        this.mapper = mapper;
    }

    public ${className} find(Long id) {
        return mapper.selectById(id);
    }

    public void insert(${className} value) {
        mapper.insert(value);
    }

    public void update(${className} value) {
        mapper.update(value);
    }
}
