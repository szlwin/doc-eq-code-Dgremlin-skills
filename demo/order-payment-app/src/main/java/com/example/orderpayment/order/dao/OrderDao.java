package com.example.orderpayment.order.dao;

import org.springframework.stereotype.Repository;
import com.dec.lite.data.CrudDao;
import com.example.orderpayment.order.entity.Order;
import com.example.orderpayment.order.mapper.OrderMapper;

/** DEC DATA-ORDER; YAML data-order.yaml#datas[id=DATA-ORDER]; XML data-order.xml data[name=order]. */
@Repository
public class OrderDao extends CrudDao<Order> {
    public OrderDao(OrderMapper mapper) {
        super(mapper);
    }
}
