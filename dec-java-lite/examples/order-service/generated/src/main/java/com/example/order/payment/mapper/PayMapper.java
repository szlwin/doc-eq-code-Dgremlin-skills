package com.example.order.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.dec.lite.data.CrudMapper;
import com.example.order.payment.entity.Pay;

/** DEC DATA-PAY; YAML data-order.yaml#datas[id=DATA-PAY]; XML data-order.xml data[name=pay]. */
@Mapper
public interface PayMapper extends CrudMapper<Pay> { }
