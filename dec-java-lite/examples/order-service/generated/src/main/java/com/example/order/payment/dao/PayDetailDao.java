package com.example.order.payment.dao;

import org.springframework.stereotype.Repository;
import com.dec.lite.data.CrudDao;
import com.example.order.payment.entity.PayDetail;
import com.example.order.payment.mapper.PayDetailMapper;

/** DEC DATA-PAY-DETAIL; YAML data-order.yaml#datas[id=DATA-PAY-DETAIL]; XML data-order.xml data[name=payDetail]. */
@Repository
public class PayDetailDao extends CrudDao<PayDetail> {
    public PayDetailDao(PayDetailMapper mapper) {
        super(mapper);
    }
}
