package com.example.order.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.dec.lite.data.CrudMapper;
import com.example.order.order.entity.Order;

/** DEC DATA-ORDER; YAML data-order.yaml#datas[id=DATA-ORDER]; XML data-order.xml data[name=order]. */
@Mapper
public interface OrderMapper extends CrudMapper<Order> { }
