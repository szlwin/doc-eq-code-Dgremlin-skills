package com.example.orderpayment.order.mapper;

import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import com.example.orderpayment.order.entity.OrderDetail;
import com.example.orderpayment.order.enums.OrderStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Primary
public final class LocalOrderDetailMapperAdapter implements OrderDetailMapper {
    private final JdbcTemplate jdbc;
    LocalOrderDetailMapperAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = "SELECT od_id,od_order_id,od_user_id,od_product_id,od_product_name,od_product_price,od_product_amount,od_status FROM dec_demo_order_detail_info";
    private static final RowMapper<OrderDetail> ROW = (r,n) -> { OrderDetail v=new OrderDetail(); v.setId(r.getInt("od_id")); v.setOrderId((Integer)r.getObject("od_order_id")); v.setUserId((Integer)r.getObject("od_user_id")); v.setProductId((Integer)r.getObject("od_product_id")); v.setProductName(r.getString("od_product_name")); v.setProductPrice(r.getBigDecimal("od_product_price")); v.setProductAmount(r.getBigDecimal("od_product_amount")); v.setStatus(OrderStatus.fromValue((Integer)r.getObject("od_status"))); return v; };
    @Override public OrderDetail selectById(Long id) {
        return jdbc.query(SELECT + " WHERE od_id=?", ROW, id).stream().findFirst().orElse(null);
    }
    public List<OrderDetail> selectByOrderId(Long id) {
        return jdbc.query(SELECT + " WHERE od_order_id=? ORDER BY od_id", ROW, id);
    }
    @Override public void insert(OrderDetail v) { jdbc.update("INSERT INTO dec_demo_order_detail_info VALUES (?,?,?,?,?,?,?,?)", v.getId(),v.getOrderId(),v.getUserId(),v.getProductId(),v.getProductName(),v.getProductPrice(),v.getProductAmount(),v.getStatus().getValue()); }
    @Override public void update(OrderDetail v) { jdbc.update("UPDATE dec_demo_order_detail_info SET od_order_id=?,od_user_id=?,od_product_id=?,od_product_name=?,od_product_price=?,od_product_amount=?,od_status=? WHERE od_id=?", v.getOrderId(),v.getUserId(),v.getProductId(),v.getProductName(),v.getProductPrice(),v.getProductAmount(),v.getStatus().getValue(),v.getId()); }
}

