package com.example.orderpayment.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.dec.lite.data.CrudMapper;
import com.example.orderpayment.payment.entity.PayDetail;

/** DEC DATA-PAY-DETAIL; YAML data-order.yaml#datas[id=DATA-PAY-DETAIL]; XML data-order.xml data[name=payDetail]. */
@Mapper
public interface PayDetailMapper extends CrudMapper<PayDetail> { }
