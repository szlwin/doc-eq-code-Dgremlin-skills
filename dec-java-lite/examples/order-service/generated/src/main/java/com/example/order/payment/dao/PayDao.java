package com.example.order.payment.dao;

import org.springframework.stereotype.Repository;
import com.dec.lite.data.CrudDao;
import com.example.order.payment.entity.Pay;
import com.example.order.payment.mapper.PayMapper;

/** DEC DATA-PAY; YAML data-order.yaml#datas[id=DATA-PAY]; XML data-order.xml data[name=pay]. */
@Repository
public class PayDao extends CrudDao<Pay> {
    public PayDao(PayMapper mapper) {
        super(mapper);
    }
}
