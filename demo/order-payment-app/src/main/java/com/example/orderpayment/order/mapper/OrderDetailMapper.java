package com.example.orderpayment.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.dec.lite.data.CrudMapper;
import com.example.orderpayment.order.entity.OrderDetail;

/** DEC DATA-ORDER-DETAIL; YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL]; XML data-order.xml data[name=orderDetail]. */
@Mapper
public interface OrderDetailMapper extends CrudMapper<OrderDetail> { }
