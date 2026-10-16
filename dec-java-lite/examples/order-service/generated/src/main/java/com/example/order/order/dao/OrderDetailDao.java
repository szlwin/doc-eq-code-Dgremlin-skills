package com.example.order.order.dao;

import org.springframework.stereotype.Repository;
import com.dec.lite.data.CrudDao;
import com.example.order.order.entity.OrderDetail;
import com.example.order.order.mapper.OrderDetailMapper;

/** DEC DATA-ORDER-DETAIL; YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL]; XML data-order.xml data[name=orderDetail]. */
@Repository
public class OrderDetailDao extends CrudDao<OrderDetail> {
    public OrderDetailDao(OrderDetailMapper mapper) {
        super(mapper);
    }
}
