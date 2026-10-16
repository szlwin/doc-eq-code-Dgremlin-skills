package com.example.orderpayment.payment.dao;

import org.springframework.stereotype.Repository;
import com.dec.lite.data.CrudDao;
import com.example.orderpayment.payment.entity.Pay;
import com.example.orderpayment.payment.mapper.PayMapper;

/** DEC DATA-PAY; YAML data-order.yaml#datas[id=DATA-PAY]; XML data-order.xml data[name=pay]. */
@Repository
public class PayDao extends CrudDao<Pay> {
    public PayDao(PayMapper mapper) {
        super(mapper);
    }
}
