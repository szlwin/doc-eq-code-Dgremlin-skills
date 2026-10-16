package com.example.order.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.dec.lite.data.CrudMapper;
import com.example.order.user.entity.User;

/** DEC DATA-USER; YAML data-order.yaml#datas[id=DATA-USER]; XML data-order.xml data[name=user]. */
@Mapper
public interface UserMapper extends CrudMapper<User> { }
